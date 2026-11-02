//! Authentication.
//!
//! * **Microsoft** — OAuth device-code flow → Xbox Live → XSTS → Minecraft
//!   services. Requires an Azure application ("client id") that Mojang has
//!   approved for the Minecraft API; set it in Settings or via
//!   `VORTEX_MS_CLIENT_ID`. See the README.
//! * **Offline** — local username with the same UUID the vanilla server
//!   would assign (`OfflinePlayer:<name>`), for singleplayer/LAN testing.

use std::time::{Duration, SystemTime, UNIX_EPOCH};

use anyhow::{anyhow, bail, Context, Result};
use md5::{Digest, Md5};
use serde::Deserialize;
use serde_json::json;

use crate::accounts::{Account, AccountKind, MicrosoftTokens};

const DEVICE_CODE_URL: &str = "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode";
const TOKEN_URL: &str = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
const SCOPE: &str = "XboxLive.signin offline_access";
const XBL_URL: &str = "https://user.auth.xboxlive.com/user/authenticate";
const XSTS_URL: &str = "https://xsts.auth.xboxlive.com/xsts/authorize";
const MC_LOGIN_URL: &str = "https://api.minecraftservices.com/authentication/login_with_xbox";
const MC_PROFILE_URL: &str = "https://api.minecraftservices.com/minecraft/profile";

pub fn now_secs() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_secs())
        .unwrap_or(0)
}

// ---------------------------------------------------------------- offline --

/// UUID the game derives for offline players: a v3 UUID from the MD5 of
/// `OfflinePlayer:<name>` (Java's `UUID.nameUUIDFromBytes`). No dashes.
pub fn offline_uuid(username: &str) -> String {
    let mut bytes: [u8; 16] = Md5::digest(format!("OfflinePlayer:{username}").as_bytes()).into();
    bytes[6] = (bytes[6] & 0x0f) | 0x30;
    bytes[8] = (bytes[8] & 0x3f) | 0x80;
    hex::encode(bytes)
}

/// Insert dashes into a 32-character UUID.
pub fn dashed_uuid(uuid: &str) -> String {
    if uuid.len() != 32 {
        return uuid.to_string();
    }
    format!(
        "{}-{}-{}-{}-{}",
        &uuid[0..8],
        &uuid[8..12],
        &uuid[12..16],
        &uuid[16..20],
        &uuid[20..32]
    )
}

pub fn validate_username(name: &str) -> Result<()> {
    if name.is_empty() || name.len() > 16 {
        bail!("username must be 1–16 characters");
    }
    if !name.chars().all(|c| c.is_ascii_alphanumeric() || c == '_') {
        bail!("username may only contain letters, digits and _");
    }
    Ok(())
}

pub fn offline_account(username: &str) -> Result<Account> {
    validate_username(username)?;
    Ok(Account {
        uuid: offline_uuid(username),
        username: username.to_string(),
        kind: AccountKind::Offline,
    })
}

// -------------------------------------------------------------- microsoft --

#[derive(Debug, Clone, Deserialize)]
pub struct DeviceCode {
    pub device_code: String,
    /// Code the user types at [`DeviceCode::verification_uri`].
    pub user_code: String,
    pub verification_uri: String,
    pub expires_in: u64,
    #[serde(default = "default_interval")]
    pub interval: u64,
    #[serde(default)]
    pub message: String,
}

fn default_interval() -> u64 {
    5
}

#[derive(Debug, Deserialize)]
struct TokenResponse {
    access_token: Option<String>,
    refresh_token: Option<String>,
    error: Option<String>,
    error_description: Option<String>,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "PascalCase")]
struct XboxResponse {
    token: String,
    display_claims: XboxClaims,
}

#[derive(Debug, Deserialize)]
struct XboxClaims {
    xui: Vec<XboxUser>,
}

#[derive(Debug, Deserialize)]
struct XboxUser {
    uhs: String,
}

#[derive(Debug, Deserialize)]
struct XboxError {
    #[serde(rename = "XErr")]
    xerr: Option<u64>,
}

#[derive(Debug, Deserialize)]
struct McLogin {
    access_token: String,
    expires_in: u64,
}

#[derive(Debug, Deserialize)]
struct McProfile {
    id: String,
    name: String,
}

/// Step 1: ask Microsoft for a code to show the user.
pub async fn start_device_login(http: &reqwest::Client, client_id: &str) -> Result<DeviceCode> {
    let resp = http
        .post(DEVICE_CODE_URL)
        .form(&[("client_id", client_id), ("scope", SCOPE)])
        .send()
        .await?;
    let status = resp.status();
    let body = resp.text().await?;
    if !status.is_success() {
        bail!("Microsoft rejected the device code request ({status}): {body}");
    }
    serde_json::from_str(&body).context("invalid device code response")
}

/// Step 2: wait until the user has signed in, then log in to Minecraft.
pub async fn finish_device_login(
    http: &reqwest::Client,
    client_id: &str,
    code: &DeviceCode,
) -> Result<Account> {
    let deadline = now_secs() + code.expires_in;
    let mut interval = code.interval.max(1);
    let (access, refresh) = loop {
        if now_secs() > deadline {
            bail!("the sign-in code expired, please try again");
        }
        tokio::time::sleep(Duration::from_secs(interval)).await;
        let resp: TokenResponse = http
            .post(TOKEN_URL)
            .form(&[
                ("grant_type", "urn:ietf:params:oauth:grant-type:device_code"),
                ("client_id", client_id),
                ("device_code", code.device_code.as_str()),
            ])
            .send()
            .await?
            .json()
            .await?;
        match (resp.access_token, resp.error.as_deref()) {
            (Some(a), _) => break (a, resp.refresh_token.unwrap_or_default()),
            (None, Some("authorization_pending")) => continue,
            (None, Some("slow_down")) => interval += 5,
            (None, Some("authorization_declined")) => bail!("sign-in was declined"),
            (None, Some("expired_token")) => bail!("the sign-in code expired, please try again"),
            (None, err) => bail!(
                "Microsoft sign-in failed: {} {}",
                err.unwrap_or("unknown error"),
                resp.error_description.unwrap_or_default()
            ),
        }
    };
    minecraft_account(http, &access, refresh).await
}

/// Refresh an expired Microsoft account in place.
pub async fn refresh_microsoft(http: &reqwest::Client, client_id: &str, account: &mut Account) -> Result<()> {
    let AccountKind::Microsoft(tokens) = &account.kind else {
        return Ok(());
    };
    let resp: TokenResponse = http
        .post(TOKEN_URL)
        .form(&[
            ("grant_type", "refresh_token"),
            ("client_id", client_id),
            ("refresh_token", tokens.refresh_token.as_str()),
            ("scope", SCOPE),
        ])
        .send()
        .await?
        .json()
        .await?;
    let Some(access) = resp.access_token else {
        bail!(
            "could not refresh Microsoft login ({}); please sign in again",
            resp.error_description.or(resp.error).unwrap_or_default()
        );
    };
    let refresh = resp.refresh_token.unwrap_or_else(|| tokens.refresh_token.clone());
    *account = minecraft_account(http, &access, refresh).await?;
    Ok(())
}

async fn minecraft_account(http: &reqwest::Client, ms_access: &str, refresh_token: String) -> Result<Account> {
    // Xbox Live
    let xbl: XboxResponse = http
        .post(XBL_URL)
        .json(&json!({
            "Properties": {
                "AuthMethod": "RPS",
                "SiteName": "user.auth.xboxlive.com",
                "RpsTicket": format!("d={ms_access}")
            },
            "RelyingParty": "http://auth.xboxlive.com",
            "TokenType": "JWT"
        }))
        .send()
        .await?
        .error_for_status()
        .context("Xbox Live authentication failed")?
        .json()
        .await?;
    let uhs = xbl
        .display_claims
        .xui
        .first()
        .map(|x| x.uhs.clone())
        .ok_or_else(|| anyhow!("Xbox Live returned no user hash"))?;

    // XSTS
    let resp = http
        .post(XSTS_URL)
        .json(&json!({
            "Properties": { "SandboxId": "RETAIL", "UserTokens": [xbl.token] },
            "RelyingParty": "rp://api.minecraftservices.com/",
            "TokenType": "JWT"
        }))
        .send()
        .await?;
    if resp.status() == reqwest::StatusCode::UNAUTHORIZED {
        let err: XboxError = resp.json().await.unwrap_or(XboxError { xerr: None });
        bail!(xsts_error(err.xerr));
    }
    let xsts: XboxResponse = resp
        .error_for_status()
        .context("XSTS authorization failed")?
        .json()
        .await?;

    // Minecraft
    let resp = http
        .post(MC_LOGIN_URL)
        .json(&json!({ "identityToken": format!("XBL3.0 x={uhs};{}", xsts.token) }))
        .send()
        .await?;
    if resp.status() == reqwest::StatusCode::FORBIDDEN {
        bail!(
            "Minecraft services refused this app (403). Your Azure client id must be approved \
             by Mojang for the Minecraft API — see the README."
        );
    }
    let mc: McLogin = resp
        .error_for_status()
        .context("Minecraft login failed")?
        .json()
        .await?;

    let resp = http
        .get(MC_PROFILE_URL)
        .bearer_auth(&mc.access_token)
        .send()
        .await?;
    if resp.status() == reqwest::StatusCode::NOT_FOUND {
        bail!("this Microsoft account does not own Minecraft: Java Edition (or has no profile yet)");
    }
    let profile: McProfile = resp
        .error_for_status()
        .context("could not fetch Minecraft profile")?
        .json()
        .await?;

    Ok(Account {
        uuid: profile.id,
        username: profile.name,
        kind: AccountKind::Microsoft(MicrosoftTokens {
            refresh_token,
            minecraft_token: mc.access_token,
            minecraft_expires_at: now_secs() + mc.expires_in,
        }),
    })
}

fn xsts_error(code: Option<u64>) -> String {
    match code {
        Some(2148916233) => "This Microsoft account has no Xbox profile. Sign in at minecraft.net once to create one.".into(),
        Some(2148916235) => "Xbox Live is not available in your country/region.".into(),
        Some(2148916236) | Some(2148916237) => "This account needs adult verification on xbox.com (South Korea).".into(),
        Some(2148916238) => "This is a child account; an adult must add it to a Microsoft family first.".into(),
        Some(c) => format!("Xbox Live denied access (XErr {c})."),
        None => "Xbox Live denied access.".into(),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn offline_uuid_matches_java() {
        // Values from java.util.UUID.nameUUIDFromBytes("OfflinePlayer:<name>").
        assert_eq!(dashed_uuid(&offline_uuid("Notch")), "b50ad385-829d-3141-a216-7e7d7539ba7f");
        assert_eq!(offline_uuid("Steve").len(), 32);
        assert_eq!(&offline_uuid("Steve")[12..13], "3", "version 3 UUID");
    }

    #[test]
    fn username_validation() {
        assert!(validate_username("Steve_01").is_ok());
        assert!(validate_username("").is_err());
        assert!(validate_username("this_name_is_way_too_long").is_err());
        assert!(validate_username("bad name").is_err());
    }
}
