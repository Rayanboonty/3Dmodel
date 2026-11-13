# Building your iOS 27 image on a Windows PC

You downloaded `iPhone18,2_27.0.1_24A446_Restore.ipsw`. GalIOS can't use that file directly: OneiOS's scripts
turn it into the image GalIOS imports (`OneiOS_iOS27.0.1_24A446_iPhone18-2.tar.zst`). Those scripts run on Linux,
so on Windows they run inside **WSL** (Ubuntu inside Windows, free from Microsoft).

**You need:** Windows 10 (version 2004 or newer) or Windows 11, about **70 GB free on drive C:**, 8 GB of RAM or more,
and an internet connection. It takes about **1–2 hours**, mostly waiting.

## 1. Install Ubuntu (once)

1. Right-click the Start button → **Terminal (Admin)** (or **Windows PowerShell (Admin)**).
2. Type this and press Enter:

       wsl --install -d Ubuntu-24.04

3. **Restart the PC** when it asks.
4. After the restart, an **Ubuntu** window opens by itself (or open "Ubuntu" from the Start menu). It asks you to
   choose a **username** and **password** for Ubuntu. Pick anything and remember the password; it's asked again later.

If it says WSL is already installed, run `wsl --update` instead, then open Ubuntu from the Start menu.

## 2. Get the build script into Ubuntu

In the Ubuntu window, type:

    curl -fsSLO https://raw.githubusercontent.com/Rayanboonty/3Dmodel/ccr-a2c53c06-k8pnxp/GalIOS/tools/build-ios-image-wsl.sh

(Or save `build-ios-image-wsl.sh` from the chat into your Downloads folder and type
`cp /mnt/c/Users/YOUR-WINDOWS-NAME/Downloads/build-ios-image-wsl.sh ~/`, then `cd ~`.)

## 3. Run it with your .ipsw

In File Explorer, find your `.ipsw`, **Shift + right-click** it → **Copy as path**. Then in Ubuntu type
`bash build-ios-image-wsl.sh ` (with a space at the end), paste the path, and press Enter. It looks like:

    bash build-ios-image-wsl.sh "C:\Users\You\Downloads\iPhone18,2_27.0.1_24A446_Restore.ipsw"

It asks for your Ubuntu password once (to install the build tools), then works through the steps and prints each one:
tools, OneiOS, reading the IPSW, the kernel, the kernel collection, the image, the check. **Leave the window open**
and the PC awake (Settings → Power → set "Sleep" to Never while it runs).

**If it stops** (no internet for a moment, the PC went to sleep), run the same command again: finished steps are
skipped and it carries on.

## 4. Put the image on the phone

When it finishes, the image is in your **Downloads** folder: `OneiOS_iOS27.0.1_24A446_iPhone18-2.tar.zst` (about 11 GB).

1. Connect the S25 Ultra with a USB cable. On the phone, tap the USB notification → **File transfer**.
2. In File Explorer: **This PC → Galaxy S25 Ultra → Internal storage → Download**, and copy the file there.
3. On the phone: **GalIOS → Setup → step 4**. The image appears under **Found on this phone**. Tap **Import**.

Keep the image to yourself: it is built from Apple's firmware.

## If something goes wrong

The script stops with a red **STOPPED:** line saying why. Common ones:

- **"only N GB free"**: free space on drive C: (the build needs about 60 GB inside Ubuntu).
- **"/dev/fuse is missing"**: in PowerShell run `wsl --update`, then `wsl --shutdown`, reopen Ubuntu, run again.
- **The fetch says the IPSW does not match**: it isn't iPhone 17 Pro Max (iPhone18,2) iOS 27.0.1 build 24A446.
  Download exactly that one.
- **"HTTP Error 403" during the kernel step**: GitHub limits anonymous downloads per internet connection for an
  hour. Wait an hour and run again, or use a free GitHub token: on github.com go to Settings → Developer settings →
  Personal access tokens → Fine-grained → Generate (no permissions needed), then in Ubuntu type
  `export GITHUB_TOKEN=paste-the-token` and run the script again in the same window.

For anything else, copy the last 20 lines of the Ubuntu window and send them to me.
