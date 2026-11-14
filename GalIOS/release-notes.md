GalIOS 1.22 and GalIOS Studio 1.13

**Tuning (a new step in GalIOS: Setup, step 8).** Switches for how iOS runs on this phone, each one reversible, each taking effect at the next boot. They need the runtime built with this release (Setup, step 3):

- **60 Hz cool mode**: iOS draws 60 frames a second instead of 120, about half the work of its compositor, the hottest process in the logs so far.
- **Pause search indexing**: stops Spotlight's indexer, which ran a whole core for minutes. Search finds less while it is on.

Under them is a new runtime part: GalIOS can now hand iOS boot settings from the phone, but only from a short list the runtime checks. So each new switch reaches iOS without a new runtime build, and a bad one is dropped and logged instead of stopping the boot.

**The roadmap**: GalIOS/ROADMAP.md in the repository lists 37 upgrades (heat, CPU, display, screen features, hardware, tools, design), with the evidence for each and what it needs.

Also in this line of releases: the new design (1.20, 1.21), the case temperature no longer locks iOS behind "iPhone needs to cool down" (runtime galios.18), and iPhone screen features (1.19).

Both GalIOS and Studio update themselves (GalIOS with root, Studio when no task runs). If you still have GalIOS 1.17 or older, install this one once with Studio (Phone, Install GalIOS).
