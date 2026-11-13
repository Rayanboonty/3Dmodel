# iOS 27 on the Galaxy S25 Ultra: the complete Windows guide

This guide uses only your Windows PC, a USB cable and the phone. You don't need the GalIOS app or GalIOS Studio.
Two scripts do the work, both run in the **Ubuntu** window on your PC:

| Script | What it does |
| --- | --- |
| `build-ios-image-wsl.sh` | turns Apple's iPhone firmware (`.ipsw`) into the iOS 27 image, on your PC (1–3 hours) |
| `galios-adb.sh` | over USB: checks the phone, installs the boot engine, adds the runtime and your image, boots iOS, brings you back |

**Honest status:** everything here has been tested off the phone, including against OneOS's real root script. Nobody has
booted iOS on an S25 Ultra yet. If it stops somewhere, part 9 shows how to get the logs. Send them to me.

---

## 0. Checklist

- [ ] Windows 10 (version 22H2) or Windows 11, 64-bit
- [ ] **About 100 GB free on drive C:** (about 60 GB for the build inside Ubuntu, 11 GB for the finished image, 11 GB
      while it is unpacked for the copy to the phone, plus the 10.6 GB `.ipsw`)
- [ ] 16 GB of RAM is best (8 GB works, more slowly)
- [ ] Internet that stays on for a few hours (Wi-Fi is fine)
- [ ] A **USB-C cable that carries data** (the one in the phone's box works)
- [ ] On the phone: your **temporary root** method, and the **OneOS app, iOS 27 edition** (the app from the r/hackios
      post), installed. It is the boot engine: the script reads it from the phone.
- [ ] About **25 GB free on the phone**

---

## 1. Prepare Windows (once)

### 1.1 Check virtualization is on
Press **Ctrl + Shift + Esc** → **Performance** → **CPU**. Under the graph, **Virtualization** must say **Enabled**.

If it says Disabled, turn it on in the PC's BIOS/UEFI. Restart and press the setup key (often **F2**, **Del** or **F10**
as it starts). Look for **Intel VT-x / Virtualization Technology** or **AMD SVM / SVM Mode**, set it to **Enabled**,
then save and exit.

### 1.2 Install Ubuntu (WSL)
1. Right-click **Start** → **Terminal (Admin)** (or **Windows PowerShell (Admin)**) → **Yes**.
2. Paste this and press **Enter**:

       wsl --install -d Ubuntu-24.04

3. When it finishes, **restart the PC**.
4. After the restart an **Ubuntu** window opens (if not: Start menu → type **Ubuntu** → open **Ubuntu 24.04**). Wait for
   "Installing, this may take a few minutes...".
5. It asks for a **username** (lowercase, e.g. `ray`) and a **password**. While you type the password, nothing shows
   on screen. That is normal. **Remember this password.**

If step 2 said WSL is already installed, run `wsl --update` and then `wsl --install -d Ubuntu-24.04`.

### 1.3 Give Ubuntu enough memory (recommended)
1. Press **Win + R**, type `notepad %USERPROFILE%\.wslconfig` and press **Enter**. If Notepad asks, choose **Yes** to
   create the file.
2. Paste the following. With 8 GB of RAM in the PC, use `memory=6GB` instead:

       [wsl2]
       memory=12GB
       swap=16GB

3. Save and close Notepad. In the Admin terminal run `wsl --shutdown`, then open Ubuntu again.

### 1.4 Keep the PC awake while it builds
**Settings → System → Power** (on Windows 10: **Power & sleep**). Set **"When plugged in, put my device to sleep
after"** to **Never** until you're done. On a laptop, keep the charger plugged in.

---

## 2. Get the iPhone firmware (`.ipsw`)

The build needs exactly this file: **`iPhone18,2_27.0.1_24A446_Restore.ipsw`**. That is iPhone 17 Pro Max, iOS 27.0.1,
build 24A446, about 10.6 GB. Any other version or model is refused.

- Download it into your **Downloads** folder, on a site that links Apple's own servers (the one you found earlier).
- **Or skip this:** the build can fetch only the parts it needs straight from Apple. See 4.3, option B.

---

## 3. Get the tools

### 3.1 Android platform-tools (adb)
1. Download **SDK Platform-Tools for Windows** from Google: <https://developer.android.com/tools/releases/platform-tools>
   (the "Download SDK Platform-Tools for Windows" link).
2. Right-click the zip → **Extract All...** → type `C:\` as the destination → **Extract**.
3. Check that `C:\platform-tools\adb.exe` exists.

### 3.2 The two scripts
1. Save **`build-ios-image-wsl.sh`** and **`galios-adb.sh`** (from the chat) into your **Downloads** folder.
   If Windows renames one to something like `galios-adb (1).sh`, delete the older copy and rename the new one back.
2. In the Ubuntu window, paste these two lines and press **Enter**:

       W=$(wslpath "$(cmd.exe /c 'echo %USERPROFILE%' 2>/dev/null | tr -d '\r')")
       cp "$W/Downloads/build-ios-image-wsl.sh" "$W/Downloads/galios-adb.sh" ~/ && cd ~ && ls *.sh

   It should list both files.

> **Pasting in Ubuntu:** right-click inside the window, or press **Ctrl + Shift + V**. Ctrl + V alone doesn't paste
> there.

---

## 4. Build the iOS 27 image on the PC

### 4.1 First, the build tools only (about 20–40 minutes)
In Ubuntu:

    bash build-ios-image-wsl.sh --setup-only

It asks for your **Ubuntu password** (the one from 1.2), once or twice. Then it installs and builds the tools. Each step
starts with a blue **`==>`** line. A green line means the step is done.

### 4.2 Check the space
    df -h ~

The **Avail** column must show **60G** or more.

### 4.3 Build the image (1–3 hours)

**Option A: with your `.ipsw`.** In File Explorer, find the `.ipsw`, **Shift + right-click** it → **Copy as path**.
In Ubuntu, type `bash build-ios-image-wsl.sh ` (with the space at the end), paste, and press **Enter**. It looks like this:

    bash build-ios-image-wsl.sh "C:\Users\You\Downloads\iPhone18,2_27.0.1_24A446_Restore.ipsw"

**Option B: without the `.ipsw`:**

    bash build-ios-image-wsl.sh --download

You will see the steps in this order: the tools, OneiOS, reading the firmware, the kernel inputs, **building the XNU
kernel** (the longest step), the kernel collection, packing the image, and checking it.

**It's finished** when you see the green line with the Windows path of
**`OneiOS_iOS27.0.1_24A446_iPhone18-2.tar.zst`** (about 11 GB) in your **Downloads** folder.

**If it stops** (a red **STOPPED** line), read the reason. Fix it using part 10, then run **the same command
again**. Finished steps are skipped, so it carries on from where it stopped.

**GitHub limit:** if it stops with **"HTTP Error 403"** or **"rate limit"** during the kernel step, GitHub limits
anonymous downloads. Make a free token:
1. On github.com go to **Settings → Developer settings → Personal access tokens → Fine-grained tokens → Generate new
   token**.
2. Give it any name, leave every permission off, and click **Generate**. Copy the token.
3. In Ubuntu type `export GITHUB_TOKEN=` followed by the token, press **Enter**, then run the build command again in
   the same window.

Keep the image to yourself. It is made from Apple's firmware, so never upload or share it.

---

## 5. Prepare the phone

### 5.1 Developer options and USB debugging (once)
1. **Settings → About phone → Software information** → tap **Build number** 7 times, then enter your PIN.
   "Developer mode has been turned on" appears.
2. **Settings → Developer options** → turn on **USB debugging**.
3. Also in Developer options: turn on **Stay awake** (the screen stays on while charging, which helps long copies).

### 5.2 OneOS, iOS 27 edition
It must be installed on the phone. The script reads OneOS's boot engine from it and adapts its S24-only phone check
for the S25 Ultra, on your phone only. You don't need to open OneOS.

### 5.3 Start your temporary root
Do it the way you always do. Then, in your root manager (KernelSU / APatch / Magisk), **allow root for "Shell"**
(`com.android.shell`): the script talks to the phone through it.

- KernelSU / APatch: **Superuser** tab → **Shell** → switch it on.
- Magisk: the first command asks on the phone screen → **Grant**.

### 5.4 Connect
1. Plug the phone into the PC.
2. On the phone, an **"Allow USB debugging?"** box appears. Tick **Always allow from this computer** → **Allow**.
   If the box doesn't appear, unplug and plug the cable in again with the phone unlocked.

### 5.5 Check everything
In Ubuntu:

    bash galios-adb.sh check

You want to see:

    model    SM-S938B / pa3q / SM8750
    root     uid=0
    busybox  /data/adb/.../busybox
    resetprop /data/adb/.../resetprop
    volume keys ok
    free     25G or more
    OneOS    versionName=...

If a line says **MISSING** or **NOT INSTALLED**, fix it before going on (part 10).

---

## 6. Put iOS 27 on the phone

Run these one after the other. Each one ends with a green line that names the next command.

    bash galios-adb.sh install
    bash galios-adb.sh runtime
    bash galios-adb.sh image
    bash galios-adb.sh status

| Command | What it does | Time |
| --- | --- | --- |
| `install` | reads OneOS's boot engine from the phone, adapts the S25 check, installs it in `/data/local/tmp/oneios` | 10 s |
| `runtime` | downloads the newest OneiOS runtime from GitHub, checks it and copies it to the phone | 1 min |
| `image` | unpacks your image next to it in Downloads, then copies it to the phone file by file; the phone checks each file's SHA-256 | 20–50 min |
| `status` | shows what's on the phone, and the **boot check** | 10 s |

Keep the phone unlocked and plugged in during `image`. If it stops, run `bash galios-adb.sh image` again.

When `status` ends with **"Boot check passed"**, go to part 7. If it shows **`ready 0 <reason>`**, the reason
says what's missing (part 10).

When the image is on the phone, you can delete the folder `OneiOS_iOS27.0.1_24A446_iPhone18-2.unpacked` in Downloads
to get the space back. Keep the `.tar.zst` file.

---

## 7. Boot iOS 27

    bash galios-adb.sh boot

- One UI stops after about **40 seconds**. The screen goes dark, then shows the boot screen.
- iOS's first picture comes about **80 seconds** after that. The first boot can take longer.
- You can unplug the cable once One UI has stopped.

## 8. Back to One UI

- **On the phone:** hold **Volume Up + Volume Down** together for **3 seconds**.
- **Or from the PC:** `bash galios-adb.sh return`

One UI is back in about 45 seconds. If the way back fails its own checks, the phone restarts into One UI. Your data is
not touched. After a restart, temporary root is gone (part 8.1).

### 8.1 Every time after a phone restart
Everything stays on the phone (`/data/local/tmp/oneios`), so you don't copy anything again. Only:

1. Start your temporary root again.
2. Plug in the phone, then run `bash galios-adb.sh status` and then `bash galios-adb.sh boot`.

Each time you open Ubuntu later, start with `cd ~`.

---

## 9. If iOS doesn't start: get the logs

Come back to One UI first (part 8). Then:

    bash galios-adb.sh logs

That saves the logs in **Downloads\galios-logs\\<date>**. Send me **summary.txt** and the last 50 lines of
**switch-and-return.log** (or screenshots of them).

---

## 10. Problems and fixes

| What you see | What to do |
| --- | --- |
| `Virtualization` Disabled / WSL says "virtual machine platform" | Part 1.1. Then, in the Admin terminal: `wsl --install --no-distribution`, then restart |
| `STOPPED: only N GB free` | free space on C:. Empty the Recycle Bin, delete big downloads. `wsl --shutdown` releases space Ubuntu no longer uses only after a restart |
| `/dev/fuse is missing` | Admin terminal: `wsl --update`, then `wsl --shutdown`, open Ubuntu, run the same build command again |
| The build stops at **reading the firmware**: the IPSW does not match | it isn't `iPhone18,2_27.0.1_24A446`. Download exactly that one, or use `--download` |
| **HTTP Error 403 / rate limit** during the kernel step | the GitHub token in 4.3 |
| The build window was closed or the PC slept | open Ubuntu, `cd ~`, run the same build command again: it resumes |
| `adb not found` | part 3.1: `C:\platform-tools\adb.exe` must exist |
| `no phone found by adb` | a data cable, USB debugging on, the phone unlocked; try another USB port; in the Admin terminal run `C:\platform-tools\adb.exe kill-server` and try again |
| `tap Allow on the USB debugging prompt` | unlock the phone, tick **Always allow**, tap **Allow** |
| `root is not active` | start your temporary root (5.3), and allow root for **Shell** in the root manager |
| `busybox MISSING` / `resetprop MISSING` | your root manager must provide them at its usual place (`/data/adb/ksu/bin`, `/data/adb/ap/bin`, `/data/adb/magisk`). The boot needs busybox and the way back needs resetprop |
| `OneOS NOT INSTALLED` / `no iOS 27 boot engine` | install the OneOS **iOS 27 edition** |
| `OneOS's phone check is not the one known` / `checks for the S24 Ultra again` | that OneOS version changed its check: send me the line it names |
| `something is mounted in the stage` | restart the phone, start root again, run the same command again |
| `did not arrive intact (SHA-256 differs)` | the copy was damaged (cable): run the same command again |
| `ready 0 ...` in `status` | the reason after `ready 0` is OneOS's own boot check: send it to me |
| `STOPPED: the phone said: ...` | the phone refused a step and says why. Send me that line |

---

## 11. Remove everything

- From the phone: `bash galios-adb.sh remove` (frees about 11 GB). OneOS and One UI are not touched.
- From the PC: in Ubuntu, `rm -rf ~/oneios-build` (frees about 60 GB). Delete the image and the `.ipsw` in Downloads.
- Remove Ubuntu completely: Admin terminal → `wsl --unregister Ubuntu-24.04`.

Not affiliated with Apple or Samsung. iOS is a trademark of Apple Inc.; Galaxy is a trademark of Samsung Electronics.
