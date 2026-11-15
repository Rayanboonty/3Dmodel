GalaxyIOS 1.32 and GalaxyIOS Studio 1.23

**The performance pack (newest runtime, Setup, step 3).** GalaxyIOS now builds on OneiOS's newest code, which was made for exactly what your logs showed:
- Less work per touch and per frame on the Android side, and the speed boost now reaches iOS's own code.
- iOS's memory is held in place on large pages instead of being swapped.
- The GPU gives each process its level by its frame deadline, and widgets are no longer starved.
- Lower audio delay; Wi-Fi and Bluetooth wake the phone far less often.
- iOS is only told the phone is hot when it truly is, so it does not slow itself or lock for nothing.

A new GPU pack is being built from the same code; Studio's Do everything picks it up when it is published.

**Cards.** No more cut-out on a card's wallpaper: the wallpaper has more room to move, and a card's place keeps its wallpaper while the card is open.

**iOS 18.** The first kernel run is done: all 92 patches of the iPadOS 18 board layer fit Apple's iOS 18 kernel source, and 9 of the 61 patches that let iOS run beside Android fit as they are. The run now also applies every part that fits and collects the parts to rewrite.

Both the app and Studio update themselves (the app with root, Studio when no task runs).