GalIOS 1.20 and GalIOS Studio 1.11

**A new look for GalIOS.** The nebula backdrop, the violet-to-cyan gradients, the glow and the shimmer are gone. GalIOS now has a true-black canvas, cards in iOS's dark greys with One UI's generous rounding, large titles with room above them, iOS Settings-style lists with small colored icon tiles, one accent color (blue) for what you can tap, and green, orange and red only for state. Motion is a short fade; nothing loops.

**The "iPhone needs to cool down" lock screen** (the next runtime, from GalIOS/runtime/patches; get it in Setup, step 3). The phone's case temperature used to tell iOS "critical" at 46 °C, a guessed threshold, and iOS then locked itself behind that screen. Now the case can only make iOS slow down (which is what cools the phone); the battery at 50 °C and the chip at 110 °C still lock it, because those are real limits.

**iPhone screen features** (from 1.19, Setup, step 7): iOS gets the iPhone's own description of its screen (OLED panel, Dynamic Island, rounded corners), which iOS needs before it offers the always-on display and draws the island. Studio's Do everything makes the file from your IPSW and copies it to the phone; it never goes on GitHub.

Both GalIOS and Studio update themselves (GalIOS with root, Studio when no task runs). If you still have GalIOS 1.17 or older, install this one once with Studio (Phone, Install GalIOS).
