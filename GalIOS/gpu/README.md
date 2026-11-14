# GalIOS GPU pack

OneiOS's iOS-side pieces that need Apple's iOS SDK (Xcode): the Metal plug-in (GPU acceleration: without it iOS
composites on the CPU, with no wallpaper, blur or Liquid Glass), the guest helpers and the capture plug-in. They are
built by `.github/workflows/galios-gpu.yml` on GitHub's macOS runners with OneiOS's own scripts and published as a
release (`galios-gpu-*`, asset `galios-gpu-pack.tar.gz`). GalIOS Studio downloads the newest one, unpacks it into the
WSL build's `OneiOS/out/userland`, and rebuilds the image; OneiOS's image hook places and trusts the bundle.

`ONEIOS_REF` (optional) pins the OneiOS commit the pack is built from; empty: OneiOS's main.
