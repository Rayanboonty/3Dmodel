//! Minecraft server list ping: players online, message of the day, icon
//! and latency, the same way the game's multiplayer screen gets them.

use std::time::{Duration, Instant};

use anyhow::{anyhow, bail, Context, Result};
use serde::Serialize;
use tokio::io::{AsyncReadExt, AsyncWriteExt};
use tokio::net::TcpStream;

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ServerStatus {
    pub address: String,
    pub online: u32,
    pub max: u32,
    /// Message of the day, with formatting codes removed.
    pub motd: String,
    /// The server's version name, e.g. `Paper 1.21.4` or `Requires MC 1.8 / 1.21`.
    pub version: String,
    /// `data:image/png;base64,...` when the server has an icon.
    pub icon: Option<String>,
    pub ping_ms: u32,
}

/// Split `host[:port]` (port defaults to 25565).
pub fn split_address(address: &str) -> Result<(String, u16)> {
    let a = address.trim();
    if a.is_empty() {
        bail!("no server address");
    }
    match a.rsplit_once(':') {
        Some((h, p)) if !h.contains(':') => Ok((h.to_string(), p.parse().context("invalid port")?)),
        _ => Ok((a.to_string(), 25565)),
    }
}

fn put_varint(out: &mut Vec<u8>, mut v: i32) {
    loop {
        let mut b = (v & 0x7f) as u8;
        v = ((v as u32) >> 7) as i32;
        if v != 0 {
            b |= 0x80;
        }
        out.push(b);
        if v == 0 {
            break;
        }
    }
}

fn put_string(out: &mut Vec<u8>, s: &str) {
    put_varint(out, s.len() as i32);
    out.extend_from_slice(s.as_bytes());
}

fn packet(id: i32, body: &[u8]) -> Vec<u8> {
    let mut inner = Vec::new();
    put_varint(&mut inner, id);
    inner.extend_from_slice(body);
    let mut out = Vec::new();
    put_varint(&mut out, inner.len() as i32);
    out.extend(inner);
    out
}

async fn read_varint(s: &mut TcpStream) -> Result<i32> {
    let mut v: i32 = 0;
    for i in 0..5 {
        let b = s.read_u8().await?;
        v |= ((b & 0x7f) as i32) << (7 * i);
        if b & 0x80 == 0 {
            return Ok(v);
        }
    }
    bail!("bad varint from server")
}

fn varint_from(buf: &[u8], pos: &mut usize) -> Result<i32> {
    let mut v: i32 = 0;
    for i in 0..5 {
        let b = *buf.get(*pos).ok_or_else(|| anyhow!("short packet"))?;
        *pos += 1;
        v |= ((b & 0x7f) as i32) << (7 * i);
        if b & 0x80 == 0 {
            return Ok(v);
        }
    }
    bail!("bad varint in packet")
}

/// Plain text from a chat component (string, object with `text`/`extra`, or array).
fn flatten_text(v: &serde_json::Value, out: &mut String) {
    match v {
        serde_json::Value::String(s) => out.push_str(s),
        serde_json::Value::Array(a) => a.iter().for_each(|x| flatten_text(x, out)),
        serde_json::Value::Object(o) => {
            if let Some(t) = o.get("text") {
                flatten_text(t, out);
            }
            if let Some(e) = o.get("extra") {
                flatten_text(e, out);
            }
        }
        _ => {}
    }
}

/// Remove `§x` formatting codes and tidy whitespace.
pub fn strip_codes(s: &str) -> String {
    let mut out = String::with_capacity(s.len());
    let mut skip = false;
    for c in s.chars() {
        if skip {
            skip = false;
            continue;
        }
        if c == '§' {
            skip = true;
            continue;
        }
        out.push(c);
    }
    out.lines().map(str::trim).filter(|l| !l.is_empty()).collect::<Vec<_>>().join("\n")
}

pub fn parse_status(address: &str, json: &str, ping_ms: u32) -> Result<ServerStatus> {
    let v: serde_json::Value = serde_json::from_str(json).context("invalid status from server")?;
    let mut motd = String::new();
    if let Some(d) = v.get("description") {
        flatten_text(d, &mut motd);
    }
    Ok(ServerStatus {
        address: address.to_string(),
        online: v["players"]["online"].as_u64().unwrap_or(0) as u32,
        max: v["players"]["max"].as_u64().unwrap_or(0) as u32,
        motd: strip_codes(&motd),
        version: strip_codes(v["version"]["name"].as_str().unwrap_or("")),
        icon: v["favicon"].as_str().filter(|s| s.starts_with("data:image/")).map(String::from),
        ping_ms,
    })
}

/// Ask a server for its status (4 second limit).
pub async fn ping(address: &str) -> Result<ServerStatus> {
    tokio::time::timeout(Duration::from_secs(4), ping_inner(address))
        .await
        .map_err(|_| anyhow!("{address} didn't answer"))?
}

async fn ping_inner(address: &str) -> Result<ServerStatus> {
    let (host, port) = split_address(address)?;
    let started = Instant::now();
    let mut s = TcpStream::connect((host.as_str(), port)).await.with_context(|| format!("can't reach {address}"))?;
    let connect_ms = started.elapsed().as_millis() as u32;
    s.set_nodelay(true).ok();

    // Handshake (next state: status), then a status request.
    let mut hs = Vec::new();
    put_varint(&mut hs, 769); // protocol; servers answer status for any value
    put_string(&mut hs, &host);
    hs.extend_from_slice(&port.to_be_bytes());
    put_varint(&mut hs, 1);
    let mut out = packet(0x00, &hs);
    out.extend(packet(0x00, &[]));
    s.write_all(&out).await?;

    let len = read_varint(&mut s).await?;
    if !(1..=4 * 1024 * 1024).contains(&len) {
        bail!("bad status length");
    }
    let mut buf = vec![0u8; len as usize];
    s.read_exact(&mut buf).await?;
    let mut pos = 0;
    if varint_from(&buf, &mut pos)? != 0x00 {
        bail!("unexpected packet from server");
    }
    let slen = varint_from(&buf, &mut pos)? as usize;
    let json = std::str::from_utf8(buf.get(pos..pos + slen).ok_or_else(|| anyhow!("short status"))?)?;

    // Round trip: ping/pong; fall back to the connect time.
    let t = Instant::now();
    let mut ping_ms = connect_ms;
    if s.write_all(&packet(0x01, &7i64.to_be_bytes())).await.is_ok() {
        if let Ok(Ok(_)) = tokio::time::timeout(Duration::from_millis(1500), read_varint(&mut s)).await {
            ping_ms = t.elapsed().as_millis() as u32;
        }
    }
    parse_status(address, json, ping_ms.max(1))
}

#[cfg(test)]
mod tests {
    use super::*;
    use tokio::net::TcpListener;

    #[test]
    fn addresses_and_text() {
        assert_eq!(split_address("mc.hypixel.net").unwrap(), ("mc.hypixel.net".into(), 25565));
        assert_eq!(split_address("play.example.org:25570").unwrap(), ("play.example.org".into(), 25570));
        assert_eq!(strip_codes("§aHypixel §cNetwork\n  §7Sale  "), "Hypixel Network\nSale");
        let st = parse_status("x", r#"{"version":{"name":"Paper 1.21.4"},"players":{"online":12,"max":100},"description":{"text":"Hi ","extra":[{"text":"§lthere"}]}}"#, 30).unwrap();
        assert_eq!((st.online, st.max, st.motd.as_str(), st.version.as_str()), (12, 100, "Hi there", "Paper 1.21.4"));
    }

    #[tokio::test]
    async fn pings_a_local_server() {
        let listener = TcpListener::bind("127.0.0.1:0").await.unwrap();
        let port = listener.local_addr().unwrap().port();
        tokio::spawn(async move {
            let (mut c, _) = listener.accept().await.unwrap();
            // Handshake + status request.
            for _ in 0..2 {
                let len = read_varint(&mut c).await.unwrap();
                let mut b = vec![0u8; len as usize];
                c.read_exact(&mut b).await.unwrap();
            }
            let mut body = Vec::new();
            put_string(&mut body, r#"{"version":{"name":"Test"},"players":{"online":3,"max":20},"description":"§bVortex test"}"#);
            c.write_all(&packet(0x00, &body)).await.unwrap();
            let len = read_varint(&mut c).await.unwrap();
            let mut b = vec![0u8; len as usize];
            c.read_exact(&mut b).await.unwrap();
            c.write_all(&packet(0x01, &b[1..])).await.unwrap();
        });
        let st = ping(&format!("127.0.0.1:{port}")).await.unwrap();
        assert_eq!((st.online, st.max, st.motd.as_str()), (3, 20, "Vortex test"));
        assert!(st.ping_ms >= 1);
    }
}
