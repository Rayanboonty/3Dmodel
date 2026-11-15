GalaxyIOS 1.33 and GalaxyIOS Studio 1.24

**iOS no longer gets killed after a minute (newest runtime, Setup, step 3).** All eight of your sessions ended the same way. The new memory setting locked 7.4 GB for iOS, Android was left with 0.8 GB, the phone swapped constantly (that was the big lag), and Android's memory killer then ended iOS. iOS now gets about 5.4 GB and Android keeps at least 3 GB. iOS had 5.4 GB free in your logs, so it loses nothing it was using, and the phone stops swapping.

**The volume HUD on the right.** With iPhone screen features on, iOS now learns that its volume buttons are on the right edge, where the S25 Ultra's are.

**The on-phone build** now uses the exact OneiOS version GalaxyIOS is tested with, instead of whatever OneiOS's newest code is that minute. A newer kernel then gets built once with OneiOS's latest fixes.

**Cards.** The line on a card (the iOS 18 card) is gone.

**iOS 16 and iOS 18.** The kernel run now measures iOS 16 too (the kernel the Galaxy S9's iOS 16 port uses), next to iOS 18.

Both the app and Studio update themselves (the app with root, Studio when no task runs).