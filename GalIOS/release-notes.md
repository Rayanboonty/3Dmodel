GalIOS 1.19 and GalIOS Studio 1.10

**iPhone screen features (a new switch in GalIOS: Setup, step 7).** It is the first part of the always-on display and the Dynamic Island. Until now iOS got only three facts about its screen, so it never knew it had an OLED panel, a Dynamic Island or rounded corners. iOS decides from exactly those facts whether to offer the always-on display and whether to draw the island. With the switch on, iOS gets the iPhone's own description of its screen, about 200 values read from your IPSW when the image is built.

- Studio 1.10's Do everything makes the file (`OneiOS_iPhone18-2_identity.adt`, beside the image on your PC) and copies it to the phone's Download folder. It comes from Apple's firmware like the image: it never goes on GitHub.
- Then in GalIOS: Setup, iPhone screen features, Turn on. It takes effect at the next boot and needs runtime galios.17 or newer (Setup, step 3).
- It changes many things iOS knows about the screen at once, so it is a switch: if iOS gets worse, turn it off.
- Not done yet: what the always-on screen needs from the panel (a dim, slow image while iOS sleeps). The session log of a boot with the switch on shows what iOS asks for, and that is the next step.

Both GalIOS and Studio update themselves (GalIOS with root and Studio when no task runs). If you still have GalIOS 1.17 or older, install this one once with Studio (Phone, Install GalIOS).
