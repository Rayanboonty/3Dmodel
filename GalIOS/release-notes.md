GalaxyIOS 1.31 and GalaxyIOS Studio 1.22

**Your wallpapers and your phone, built in.** The iOS 16, 18, 26 and 27 cards now show their own wallpapers, and the device card shows the Galaxy S25 Ultra's photo. They are always there; there is no setting.

**Depth.** Each card's wallpaper sits deeper than the card. It moves less than the card when you swipe, and the other way when you tilt the phone, like iOS's perspective wallpapers. The big number floats above it with a soft shadow.

**The top of the app** shows the GalaxyIOS logo and name. **The phone's card** is the big one again: photo, name, state, and Setup, Logs and Refresh with their names.

**Opening Setup.** Only the card's "Set up" button grows into the Setup page. The round Setup button and the tabs just switch, with a short fade. The tab bar's glass now keeps working while Setup opens.

**Smoother iOS (newest runtime, Setup, step 3):**
- Triple buffering is on. Animations render one frame ahead, as the fastest iOS-on-Android project does; their window movement went from about 60 to about 118 frames a second. Setup, Tuning, "Triple buffering off" turns it off.
- The diagnostic tracing that ran on the keyboard and five other processes in every session is off.
- "Lighter iOS" (Setup, Tuning) now also stops photo and media analysis and analytics, which kept cores busy and the phone hot. Turning it off now really turns them back on.

**Studio** shows the S25 Ultra's photo and the iOS 27 wallpaper by default.

Both the app and Studio update themselves (the app with root, Studio when no task runs).