# iOS 18 on the Galaxy S25 Ultra: the build plan

Status, 2026-11-15: **stage 0 done, stage 1 has its worklist.** On xnu-11417.140.69 all 93 patches of the iPadOS 18 board layer apply; of the hosted layer's 62 patches 32 apply whole, and of the other 30 only 77 of 289 hunks do not fit (0001, the machine layer itself: 16 of 104). The run's artifact `ios18-porting-matrix` holds the tree as one diff against Apple's source and each rejected hunk under `rejects/`: stage 1 is rewriting those 77.
Nothing here boots yet. This file says what an iOS 18 boot needs, in order, with what already exists elsewhere.

## Why this is a new port, not a setting

GalaxyIOS boots iOS 27.0.1 (24A446) because every layer under it was made for that one build:

| Layer | iOS 27 (works) | What iOS 18 needs |
| --- | --- | --- |
| Kernel source | xnu-12377 (Droid-VM's UniXNU fork) | xnu-11417 (Apple publishes it) |
| Board layer (what iOS needs from a non-Apple machine) | UniXNU-droidvm `ipados27/patches/xnu-12377` | UniXNU-y2q `patches/xnu-ipados18` exists (bare metal, iPadOS 18.7.10) |
| Hosted layer (XNU as a process beside Android: why the S25 Ultra's locked bootloader does not matter) | OneiOS `kernel/patches/oneios-hosted`, 61 patches | **Nobody has it for xnu-11417**: to port |
| Prelink (binding the kernel collection's kexts) | OneiOS reviewed records for 24A446 | redone for the iOS 18 build |
| Drivers (GPU, display, touch, audio, battery... as virtio guests) | built against 24A446's kernel collection | rebuilt against the iOS 18 collection |
| Userland image | profile `ios-27.0.1-iPhone18,2` | a new profile for the iOS 18 build |
| Metal on the Adreno | NativeMetal + metal2vulkan + Turnip, hooks found in 24A446's shared cache | hooks re-found in iOS 18's shared cache (y2q has an iOS 18 Metal layer: AdrenoMetal/PongMetal) |

Other phones that run iOS 16 (ios-sweet and its forks, the Galaxy S9's ios-s9) run it **bare metal**: they replace
Android and need an unlocked bootloader. The S25 Ultra's bootloader is locked, so GalaxyIOS can only run iOS as a
process beside Android (OneiOS's way). That hosted layer is what has to be ported; the rest has precedents.

## The target

**iPadOS 18.7.10 (22H374) with the iPad7,11 identity** first, not an iPhone build: it is the one iOS 18 that
already boots on a Snapdragon phone (UniXNU-y2q, xnu-11417.140.69, 4 KiB pages, Turnip and a Metal frontend), so its
board layer, prelink inputs and Metal layer are a starting point instead of a blank page. An iPhone iOS 18 (for
example iPhone17,2) can follow once the hosted layer runs.

## Stages

0. **Porting matrix** (now, on GitHub): xnu-11417 + y2q's board layer + OneiOS's patches; which apply and which must
   be ported. Output: the run's summary and the artifact `ios18-porting-matrix`.
1. **Hosted layer ported to xnu-11417**: each "to port" patch of stage 0 rewritten against xnu-11417's code (the
   machine layer, SMP and timers, copyio, GPU 3D, root mount, prelink start, software AES, shared region). Built on
   GitHub (OneiOS's `kernel/build-hosted.sh` way: clang and xnu-linux-toolchain, no Mac).
2. **Kernel collection for 22H374**: the prelink records for that build (OneiOS's `kernel/prelink`, y2q's inputs).
3. **Drivers**: OneiOS's virtio guests (input, display, audio, battery, GPU user client) built against the 22H374
   collection.
4. **Image**: an image profile for 22H374 in the image build (the PC's WSL build and the on-phone build), reading
   the owner's own IPSW as for iOS 27. No Apple file is ever in this repository or its releases.
5. **Metal**: the plug-in's hooks for 22H374's shared cache (needs a Mac to build the plug-in, as for iOS 27's).
6. **GalaxyIOS**: the iOS 18 card becomes bootable; each iOS gets its own stage and Data store, so iOS 27 stays.

## What this needs that cannot be skipped

- Every stage after 0 is verified only on the phone: a session log per attempt, as for iOS 27.
- Stage 5 needs a Mac (Xcode) once per build, as iOS 27's GPU pack did.
- Each stage is weeks of kernel work for the people who did iOS 27's (OneiOS's own record: the hosted layer took
  61 patches and months). The matrix makes the size of stage 1 a number instead of a guess.
