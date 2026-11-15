GalaxyIOS 1.30 and GalaxyIOS Studio 1.21

From your session log (15:11): iOS was slow for two reasons, both fixed in the newest runtime. Get it in Setup, step 3; Studio's Do everything gets it by itself.

**The two fastest cores now run iOS.** Android had started the session where the two prime cores were off limits. iOS's two fastest processors were squeezed onto one small core for the whole session. The runtime now moves itself to where every core is allowed, so each of iOS's processors gets its own core.

**Maximum performance no longer makes iOS crawl.** It gave iOS a fixed 10 GB, but only 4.9 GB was free beside Android, so the phone swapped iOS's memory to storage. Now iOS gets what fits, and maximum performance only boosts every core while iOS animates.

**iPhone screen features (from 1.29)** give iOS only the screen details: the Dynamic Island, the OLED panel, the corners and the always-on display. iOS keeps this phone's own graphics and touch.

Also in 1.29: cards open smoothly from where they sit, black card buttons, no press glow, flat colours everywhere.

Both the app and Studio update themselves (the app with root, Studio when no task runs).