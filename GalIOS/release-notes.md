GalaxyIOS 1.26 and GalaxyIOS Studio 1.17

**True black.** The app and Studio are now pure black, with frosted glass cards and one white button for the main action. The tab bar at the bottom of the app is frosted: the page blurs behind it as you scroll. Studio's sidebar and task bar are frosted the same way.

**Swipe between iOS versions.** The Boot tab has a card for each iOS (16, 18, 26 and 27). Swipe to see each one and whether it runs on this phone. iOS 27 is the one optimized for the S25 Ultra, and it boots from its card. The others say why they cannot boot here yet. The device row above the cards is smaller and more compact.

**iPhone screen features without a PC.** In Setup, step 7, tap "Choose your IPSW" and pick iPhone18,2_27.0.1_24A446_Restore.ipsw on your phone. GalaxyIOS reads the iPhone's screen description (its device tree) out of the IPSW and turns the switch on. That is what iOS needs before it offers the always-on display and draws the Dynamic Island. The IPSW is only read: nothing leaves your phone. If that IPSW keeps the description compressed or encrypted, GalaxyIOS says so, and Studio's Do everything makes it instead.

**Studio's Do everything turns iPhone screen features on.** After copying the image, it copies the iPhone's description and switches the feature on. The Phone page has Turn on and Turn off buttons for it. Turn it off if iOS behaves worse; it takes effect at the next boot. It needs runtime galios.16 or newer.

Both the app and Studio update themselves (the app with root, Studio when no task runs). If you still have version 1.17 or older, install this one once with Studio (Phone, Install GalaxyIOS).
