// Demo backend used when the UI runs in a normal browser (design previews,
// screenshots). Mirrors the Tauri commands with in-memory data.

const listeners = new Map();
const emit = (event, payload) => (listeners.get(event) || []).forEach((h) => h(payload));
const wait = (ms) => new Promise((r) => setTimeout(r, ms));

const now = Math.floor(Date.now() / 1000);

// A snapshot of real Modrinth results (most downloaded) for previews.
const MODRINTH = {
  mod: [{"id": "P7dR8mSH", "slug": "fabric-api", "title": "Fabric API", "description": "Lightweight and modular API providing common hooks and intercompatibility measures utilized by mods using the Fabric toolchain.", "author": "modmuss50", "downloads": 295694437, "follows": 37497, "iconUrl": "https://cdn.modrinth.com/data/P7dR8mSH/icon.png", "categories": ["library"], "updated": "2026-11-03", "gallery": null, "color": "#bcb49c"}, {"id": "AANobbMI", "slug": "sodium", "title": "Sodium", "description": "A high-performance rendering engine replacement for Minecraft, which greatly improves frame rates and reduces micro-stutter.", "author": "jellysquid3", "downloads": 258929242, "follows": 42430, "iconUrl": "https://cdn.modrinth.com/data/AANobbMI/295862f4724dc3f78df3447ad6072b2dcd3ef0c9_96.webp", "categories": ["optimization"], "updated": "2026-10-15", "gallery": null, "color": "#84cc6c"}, {"id": "YL57xq9U", "slug": "iris", "title": "Iris Shaders", "description": "A modern shader pack loader for Minecraft intended to be compatible with existing OptiFine shader packs", "author": "coderbot", "downloads": 200167674, "follows": 30476, "iconUrl": "https://cdn.modrinth.com/data/YL57xq9U/18d0e7f076d3d6ed5bedd472b853909aac5da202_96.webp", "categories": ["decoration", "optimization"], "updated": "2026-09-30", "gallery": "https://cdn.modrinth.com/data/YL57xq9U/images/f47cea26f135d74c2d69df0a1806751818d705d3_350.webp", "color": "#040404"}, {"id": "NNAgCjsB", "slug": "entityculling", "title": "Entity Culling", "description": "Using async path-tracing to hide Block-/Entities that are not visible", "author": "tr7zw", "downloads": 194418668, "follows": 18134, "iconUrl": "https://cdn.modrinth.com/data/NNAgCjsB/7873452d6cede4daed12da3d7d8c193ab88b4fd6_96.webp", "categories": ["babric", "optimization"], "updated": "2026-10-08", "gallery": "https://cdn.modrinth.com/data/NNAgCjsB/images/65781fbd7cda31b8c8e4a8def40bf445c10a1562.gif", "color": "#5c5c5b"}, {"id": "9s6osm5g", "slug": "cloth-config", "title": "Cloth Config API", "description": "Configuration Library for Minecraft Mods", "author": "shedaniel", "downloads": 189907387, "follows": 17059, "iconUrl": "https://cdn.modrinth.com/data/9s6osm5g/ed8a2316cbb6f4fc5f510e8e13a59a85cbbbff4d_96.webp", "categories": ["library"], "updated": "2026-09-23", "gallery": "https://cdn.modrinth.com/data/9s6osm5g/images/baa0dc605540953f5262f24109ec7837c090a04c.png", "color": "#b1d660"}, {"id": "uXXizFIs", "slug": "ferrite-core", "title": "FerriteCore", "description": "Memory usage optimizations", "author": "malte0811", "downloads": 170975335, "follows": 16861, "iconUrl": "https://cdn.modrinth.com/data/uXXizFIs/222a126f26f8f9ae1eb339f3b767677f18bff31f_96.webp", "categories": ["optimization", "utility"], "updated": "2026-03-24", "gallery": null, "color": "#4e4644"}, {"id": "mOgUt4GM", "slug": "modmenu", "title": "Mod Menu", "description": "Adds a mod menu to view the list of mods you have installed.", "author": "Prospector", "downloads": 164976763, "follows": 28024, "iconUrl": "https://cdn.modrinth.com/data/mOgUt4GM/5a20ed1450a0e1e79a1fe04e61bb4e5878bf1d20.png", "categories": ["utility"], "updated": "2026-10-27", "gallery": "https://cdn.modrinth.com/data/mOgUt4GM/images/70fd54b0579f2de3ac45fa9650aca2ec8ff0882b_350.webp", "color": "#144cfc"}, {"id": "gvQqBUqZ", "slug": "lithium", "title": "Lithium", "description": "No-compromises game logic optimization mod, useful for both single-player games and multi-player servers.", "author": "jellysquid3", "downloads": 146630441, "follows": 24193, "iconUrl": "https://cdn.modrinth.com/data/gvQqBUqZ/bcc8686c13af0143adf4285d741256af824f70b7_96.webp", "categories": ["optimization"], "updated": "2026-10-28", "gallery": null, "color": "#eaeaf3"}, {"id": "5ZwdcRci", "slug": "immediatelyfast", "title": "ImmediatelyFast", "description": "Speed up immediate mode rendering in Minecraft", "author": "RaphiMC", "downloads": 141299404, "follows": 11875, "iconUrl": "https://cdn.modrinth.com/data/5ZwdcRci/e57b6b451425692ac17ad322d5e14bea686a383a_96.webp", "categories": ["optimization"], "updated": "2026-10-13", "gallery": null, "color": "#2b2d2d"}, {"id": "1eAoo2KR", "slug": "yacl", "title": "YetAnotherConfigLib (YACL)", "description": "A builder-based configuration library for Minecraft!", "author": "isxander", "downloads": 138638186, "follows": 9876, "iconUrl": "https://cdn.modrinth.com/data/1eAoo2KR/08c0cd32515e260f4bb20bbc0696510041523f9a_96.webp", "categories": ["library", "management", "utility"], "updated": "2026-10-06", "gallery": "https://cdn.modrinth.com/data/1eAoo2KR/images/5862570281f5109119c11f21a1bba52b6a2ab17f_350.webp", "color": "#222221"}, {"id": "Ha28R6CL", "slug": "fabric-language-kotlin", "title": "Fabric Language Kotlin", "description": "This is a mod that enables usage of the Kotlin programming language for Fabric mods.", "author": "modmuss50", "downloads": 134987064, "follows": 11367, "iconUrl": "https://cdn.modrinth.com/data/Ha28R6CL/72c3d74aeb665e45aea93a945a01474cbce3b7da_96.webp", "categories": ["library"], "updated": "2026-10-09", "gallery": null, "color": "#bc1ce4"}, {"id": "1bokaNcj", "slug": "xaeros-minimap", "title": "Xaero's Minimap", "description": "Displays a map of the nearby world terrain, players, mobs, entities in the corner of your screen. Lets you create waypoints which help you f", "author": "thexaero", "downloads": 123728848, "follows": 19196, "iconUrl": "https://cdn.modrinth.com/data/1bokaNcj/354080f65407e49f486fcf9c4580e82c45ae63b8_96.webp", "categories": ["adventure", "transportation", "utility"], "updated": "2026-10-25", "gallery": null, "color": "#3342c6"}],
  resourcepack: [{"id": "50dA9Sha", "slug": "fresh-animations", "title": "Fresh Animations", "description": "Make your game like the trailers! Dynamic animated entities to freshen your Minecraft experience.", "author": "FreshLX", "downloads": 53130496, "follows": 14764, "iconUrl": "https://cdn.modrinth.com/data/50dA9Sha/3132c10e9e3c73fde9799720fd3da5561071708c_96.webp", "categories": ["entities", "minecraft", "tweaks"], "updated": "2026-11-01", "gallery": "https://cdn.modrinth.com/data/50dA9Sha/images/26e5c0a8a65d421b1111e2c68880f1d657fdc920_350.webp", "color": "#cfa4a0"}, {"id": "yfDziwn1", "slug": "translations-for-sodium", "title": "Translations for Sodium", "description": "Unofficial translations for the Sodium Minecraft mod", "author": "robotkoer", "downloads": 23922159, "follows": 1671, "iconUrl": "https://cdn.modrinth.com/data/yfDziwn1/907581019df45903df237952ce8d10ac37134cb5_96.webp", "categories": ["locale", "minecraft", "modded"], "updated": "2026-10-31", "gallery": "https://cdn.modrinth.com/data/yfDziwn1/images/41175c3da02a8737e1a474e47ff172e96d550149.png", "color": "#d6fbd6"}, {"id": "uvpymuxq", "slug": "better-leaves", "title": "Motschen's Better Leaves", "description": "Improves the appearance of leaves with high mod compatibility and performance!", "author": "Motschen", "downloads": 22098801, "follows": 5490, "iconUrl": "https://cdn.modrinth.com/data/uvpymuxq/fe1a61998ae57dc6ad1a4bb028334c3c3925d22f_96.webp", "categories": ["minecraft", "models", "realistic"], "updated": "2026-09-14", "gallery": "https://cdn.modrinth.com/data/uvpymuxq/images/769f109a7d5a753ea93c8520bbaaace9f3059ea2_350.webp", "color": "#0a4e04"}, {"id": "tN4E9NfV", "slug": "chat-reporting-helper", "title": "Chat Reporting Helper", "description": "An educational tool that explains chat reporting in a simple and neutral way.", "author": "robotkoer", "downloads": 18563765, "follows": 286, "iconUrl": "https://cdn.modrinth.com/data/tN4E9NfV/c2d93fd85469a512f67e53baa7648c1abe6645ef.png", "categories": ["locale", "minecraft", "simplistic"], "updated": "2026-09-27", "gallery": "https://cdn.modrinth.com/data/tN4E9NfV/images/5eb8b8469192851bc4c2095f455f527cb413c12a.png", "color": "#fbdb65"}, {"id": "RRxvWKNC", "slug": "low-on-fire", "title": "Low On Fire", "description": "Low fire on your screen! Vanilla Friendly", "author": "Haikis", "downloads": 18174502, "follows": 3897, "iconUrl": "https://cdn.modrinth.com/data/RRxvWKNC/06a7e7691a6da41798c255108a8563d7ffea171d.png", "categories": ["16x", "minecraft", "tweaks"], "updated": "2026-06-19", "gallery": "https://cdn.modrinth.com/data/RRxvWKNC/images/67e073e6149cfb9dd46c7a53cf9b8a995f774595_350.webp", "color": "#e8bc41"}, {"id": "slufHzC2", "slug": "tras-fresh-player", "title": "Fresh Moves", "description": "This is EMF Player Animation resource pack. Gives fancy animations to the player entity. Compatible with animations from other mods. Like Qu", "author": "IthanMendoza", "downloads": 15959822, "follows": 3996, "iconUrl": "https://cdn.modrinth.com/data/slufHzC2/02b78db6655ad8dd6cfd847f99c76f7552c053ec_96.webp", "categories": ["16x", "equipment", "minecraft"], "updated": "2025-01-20", "gallery": "https://cdn.modrinth.com/data/slufHzC2/images/7d72ccd7e75f18ab9bf1c804d9020c5ab89c5129_350.webp", "color": "#3b3834"}, {"id": "dspVZXKP", "slug": "fast-better-grass", "title": "Fast Better Grass", "description": "Makes grass and related blocks use the top texture on the sides. Works with other resource packs!", "author": "robotkoer", "downloads": 14392414, "follows": 2739, "iconUrl": "https://cdn.modrinth.com/data/dspVZXKP/5de0774f423d220fcd4e635b91f2bdfcb8a2b910.png", "categories": ["decoration", "minecraft", "models"], "updated": "2026-09-15", "gallery": "https://cdn.modrinth.com/data/dspVZXKP/images/44b46fae71c21058a904311d99f63d4881d24b1c_350.webp", "color": "#22381c"}, {"id": "ItHr72Fy", "slug": "fullbright-ub", "title": "Fullbright UB", "description": "Experience Fullbright UB in Sodium, Optifine and Vanilla", "author": "worldresourcepack", "downloads": 12779070, "follows": 2751, "iconUrl": "https://cdn.modrinth.com/data/ItHr72Fy/c28c5e4ae4e1e5ac726ff192855d16d2c15c83e2_96.webp", "categories": ["core-shaders", "minecraft", "vanilla-like"], "updated": "2026-09-19", "gallery": "https://cdn.modrinth.com/data/ItHr72Fy/images/56ca455a62a430402cfe80d5f42f2697ee1632f2.webp", "color": "#181818"}, {"id": "YAVTU8mK", "slug": "fresh-animations-extensions", "title": "Fresh Animations: Extensions", "description": "A combination of offical extension packs for Fresh Animations", "author": "FreshLX", "downloads": 12274703, "follows": 3577, "iconUrl": "https://cdn.modrinth.com/data/YAVTU8mK/2e83da75469cdec6d4558023cba611443b105c9e_96.webp", "categories": ["entities", "minecraft", "tweaks"], "updated": "2026-10-31", "gallery": "https://cdn.modrinth.com/data/YAVTU8mK/images/5a1d78039b6f64ac429f24e6d98f133ae998764e_350.webp", "color": "#b18cab"}, {"id": "2YyNMled", "slug": "dramatic-skys", "title": "Dramatic Skys", "description": "Dramatic Skys is an add on pack that will make the skies in your game HD with realistic clouds and lighting. This pack works with any other ", "author": "thebaum64", "downloads": 11756633, "follows": 3805, "iconUrl": "https://cdn.modrinth.com/data/2YyNMled/75af81c8e31ad9b2848bff6fc40c9d7232f3c3b0_96.webp", "categories": ["environment", "minecraft", "realistic"], "updated": "2026-09-18", "gallery": "https://cdn.modrinth.com/data/2YyNMled/images/87c986c172dd772947444c4650cef8208433a77a_350.webp", "color": "#4f253a"}, {"id": "gfiYlQU0", "slug": "cobblemon-interface", "title": "Cobblemon Interface", "description": "Cobblemon inspired GUI Textures and Sounds", "author": "VinnyStalck", "downloads": 10228967, "follows": 391, "iconUrl": "https://cdn.modrinth.com/data/gfiYlQU0/d3797ea2a31bc002182554d1e1957c3aa27aa877.png", "categories": ["audio", "gui", "minecraft"], "updated": "2025-11-14", "gallery": "https://cdn.modrinth.com/data/gfiYlQU0/images/0b58e578696610b931b2eb8788994f8dd2f187a9_350.webp", "color": "#383838"}, {"id": "oL18adaQ", "slug": "new-glowing-ores", "title": "New Glowing Ores", "description": "Makes ores glow to make them more visible, and also adds connected outlines to them so you don't miss a single block. Mod support available", "author": "GridExpert", "downloads": 8945392, "follows": 3875, "iconUrl": "https://cdn.modrinth.com/data/oL18adaQ/2bb2608c4f8c4801cbbdd3c60673b3c2228c5027.png", "categories": ["blocks", "decoration", "minecraft"], "updated": "2024-10-23", "gallery": "https://cdn.modrinth.com/data/oL18adaQ/images/293c5b6ac00a647d39a47afc369c5ad7234f471f_350.webp", "color": "#3d4e3f"}],
  shader: [{"id": "HVnmMxH1", "slug": "complementary-reimagined", "title": "Complementary Shaders - Reimagined", "description": "Preserving the elements of Minecraft with exceptional quality, detail, and performance.", "author": "EminGT", "downloads": 74376886, "follows": 11469, "iconUrl": "https://cdn.modrinth.com/data/HVnmMxH1/79cb7c8123bbc54945305b2ebad6b8881efdf5f8_96.webp", "categories": ["colored-lighting", "iris", "optifine"], "updated": "2026-09-15", "gallery": "https://cdn.modrinth.com/data/HVnmMxH1/images/111e76bfa966c68b3581a93f7322588e642ce4db_350.webp", "color": "#d8a4bd"}, {"id": "R6NEzAwj", "slug": "complementary-unbound", "title": "Complementary Shaders - Unbound", "description": "Transforming the visuals of Minecraft with exceptional quality, detail, and performance.", "author": "EminGT", "downloads": 48175173, "follows": 6098, "iconUrl": "https://cdn.modrinth.com/data/R6NEzAwj/c85ce4049aac76360d2cd24fd9a7003de01ef312_96.webp", "categories": ["colored-lighting", "fantasy", "iris"], "updated": "2026-09-15", "gallery": "https://cdn.modrinth.com/data/R6NEzAwj/images/a5a420ab1668bcfe47a76a812eede7056c45fe31_350.webp", "color": "#282344"}, {"id": "Q1vvjJYV", "slug": "bsl-shaders", "title": "BSL Shaders", "description": "Shaderpack for Minecraft: Java Edition. It's bright, colorful, and distinct.", "author": "CaptTatsu", "downloads": 32536721, "follows": 6316, "iconUrl": "https://cdn.modrinth.com/data/Q1vvjJYV/2a611a3cb434fb52fb81fa5dace13c5d8b67e55d_96.webp", "categories": ["bloom", "cartoon", "iris"], "updated": "2026-09-21", "gallery": "https://cdn.modrinth.com/data/Q1vvjJYV/images/40b899ae09647c98722d67bb2288f790ae28b4fc_350.webp", "color": "#5ca4f4"}, {"id": "lLqFfGNs", "slug": "photon-shader", "title": "Photon Shaders", "description": "A gameplay-focused shader pack with a semi-realistic style", "author": "sixthsurge", "downloads": 30131011, "follows": 4392, "iconUrl": "https://cdn.modrinth.com/data/lLqFfGNs/39cb5f12e7dcc68d6cb666f225fcb2b801dd70fb_96.webp", "categories": ["atmosphere", "colored-lighting", "iris"], "updated": "2026-10-19", "gallery": "https://cdn.modrinth.com/data/lLqFfGNs/images/a7205115d731077733ab5cdcc639e5531f2dee12_350.webp", "color": "#a76f59"}, {"id": "EpQFjzrQ", "slug": "solas-shader", "title": "Solas Shader", "description": "A modern fantasy stylized shaderpack with colored lighting and stunning visuals", "author": "Septonious", "downloads": 19238897, "follows": 3540, "iconUrl": "https://cdn.modrinth.com/data/EpQFjzrQ/e3efc6ba7a63f9e1cf473a794d0224a6daf243c7_96.webp", "categories": ["colored-lighting", "fantasy", "iris"], "updated": "2026-11-02", "gallery": "https://cdn.modrinth.com/data/EpQFjzrQ/images/b87a4f77d7858c6b8fb9ecc40301f9d189a52c44_350.webp", "color": "#f3ae6e"}, {"id": "ZvMtQlho", "slug": "bliss-shader", "title": "Bliss Shaders", "description": "A well performing fantasy styled shaderpack with emphasis on scene variation and customization.", "author": "Xonk", "downloads": 16223274, "follows": 2992, "iconUrl": "https://cdn.modrinth.com/data/ZvMtQlho/90145c971ea24387775108fc86c89bed9bd2c8f1_96.webp", "categories": ["atmosphere", "fantasy", "iris"], "updated": "2025-11-23", "gallery": "https://cdn.modrinth.com/data/ZvMtQlho/images/7ceb3b0fcd9ccdbd5fc4bdc80ba7ee766509c69f_350.webp", "color": "#45486f"}, {"id": "izsIPI7a", "slug": "makeup-ultra-fast-shaders", "title": "MakeUp - Ultra Fast", "description": "MakeUp aims to provide the best quality / performance ratio, building a shader that can be adapted to anyone's resources.", "author": "KDXavier", "downloads": 14663096, "follows": 2447, "iconUrl": "https://cdn.modrinth.com/data/izsIPI7a/a08432baa86b8ffd58c08f4b3a001ef976ff764d_96.webp", "categories": ["iris", "optifine", "potato"], "updated": "2026-10-19", "gallery": "https://cdn.modrinth.com/data/izsIPI7a/images/97776ad4f19f734243dbb301feaf81c327bae96b_350.webp", "color": "#1e3a66"}, {"id": "kmwfVOoi", "slug": "rethinking-voxels", "title": "Rethinking Voxels", "description": "[WIP] A gameplay shaderpack based on complementary reimagined that has coloured block light with sharp shadows", "author": "gri573", "downloads": 14032376, "follows": 4420, "iconUrl": "https://cdn.modrinth.com/data/kmwfVOoi/fc89eadad417dd376b14c3b31e1a2b87acaca034_96.webp", "categories": ["colored-lighting", "iris", "optifine"], "updated": "2025-06-19", "gallery": "https://cdn.modrinth.com/data/kmwfVOoi/images/c2de214a7981a9b0235b46209507eb9c0b225ddc_350.webp", "color": "#4a2937"}, {"id": "BUxf36AP", "slug": "mellow", "title": "Mellow", "description": "Shaderpack with a painterly aesthetic made for people who want to actually play the game. It's easy on your eyes and on your computer!", "author": "TheCMK", "downloads": 8540321, "follows": 1405, "iconUrl": "https://cdn.modrinth.com/data/BUxf36AP/b2cc2e9b97e5e0c4c7fcd89b7e295f29a29b9c6d_96.webp", "categories": ["atmosphere", "fantasy", "iris"], "updated": "2026-10-31", "gallery": "https://cdn.modrinth.com/data/BUxf36AP/images/184d65af1f3f222be773ec0c742932ecc2639116_350.webp", "color": "#e2b5bb"}, {"id": "LMIZZNxZ", "slug": "super-duper-vanilla", "title": "Super Duper Vanilla", "description": "A shader pack created to convey the style of the cancelled Super Duper Graphics Pack and other popular Minecraft titles.", "author": "Eldeston", "downloads": 8475929, "follows": 2640, "iconUrl": "https://cdn.modrinth.com/data/LMIZZNxZ/5d09a380b6da014dcbca683879cdfb4a94603cf8_96.webp", "categories": ["iris", "low", "optifine"], "updated": "2025-07-08", "gallery": "https://cdn.modrinth.com/data/LMIZZNxZ/images/a1b45a0d10ff893cb4247979ced0ff0b9ce182a9_350.webp", "color": "#c38a32"}, {"id": "EQWX2WiO", "slug": "insanity-shader", "title": "Insanity Shader", "description": "A stylized, horror themed Iris shaderpack with high customizability and performance.", "author": "ElocinDev", "downloads": 8216175, "follows": 1278, "iconUrl": "https://cdn.modrinth.com/data/EQWX2WiO/cc7a7e0bb758af1ed4208d297ab14c92e695c999_96.webp", "categories": ["atmosphere", "fantasy", "iris"], "updated": "2024-06-13", "gallery": "https://cdn.modrinth.com/data/EQWX2WiO/images/c6f41d1eef4bcd4b43619f59e0c3a74b079ad283_350.webp", "color": "#141414"}, {"id": "RphJSnEs", "slug": "astralex", "title": "AstraLex Shaders", "description": "Shaderpack with very high customization. If you like effects, this shaderpack is made for you!", "author": "LexBoosT", "downloads": 5952184, "follows": 1210, "iconUrl": "https://cdn.modrinth.com/data/RphJSnEs/3e25ea407447bf2ff8ffa8926cd2db295307cf68_96.webp", "categories": ["atmosphere", "cartoon", "fantasy"], "updated": "2024-06-16", "gallery": "https://cdn.modrinth.com/data/RphJSnEs/images/a8a1137931740b5e6d59a12ce3bfb103c3d9397b_350.webp", "color": "#211516"}],

};
const ART = [{"version": "26.3", "image": "https://launchercontent.mojang.com/v2/images/dappledcamp540x540.jpg"}, {"version": "26.2", "image": "https://launchercontent.mojang.com/v2/images/26.2540x540.jpg"}, {"version": "1.21.4", "image": "https://launchercontent.mojang.com/v2/images/1.21.4540x540.jpg"}, {"version": "1.21.1", "image": "https://launchercontent.mojang.com/v2/images/1.21.1540x540.jpg"}, {"version": "1.20.4", "image": "https://launchercontent.mojang.com/v2/images/78lvSIdTl1HmVNoHTTnYkAMinecraft1.20.4.jpeg"}, {"version": "1.20.1", "image": "https://launchercontent.mojang.com/v2/images/5GW9TGsMULr7xEsDrhxS3n1.20.1.jpeg"}, {"version": "1.19.4", "image": "https://launchercontent.mojang.com/v2/images/7lKi3Yf0Qyt3y6hniqecG11.19.4.jpeg"}, {"version": "1.18.2", "image": "https://launchercontent.mojang.com/v2/images/5hKF2GUp8XiOAmxBTA2lK9MinecraftJavaEdition1.18.2.jpeg"}, {"version": "1.16.5", "image": "https://launchercontent.mojang.com/v2/images/2q0u4Zq5AM17pwj5twUIQ0MinecraftJavaEdition1.16.5.jpeg"}, {"version": "26.4-snapshot-6", "image": "https://launchercontent.mojang.com/v2/images/26.4snapshot6540x540.jpg"}, {"version": "1.21.11", "image": "https://launchercontent.mojang.com/v2/images/1.21.11540x540.jpg"}, {"version": "1.21.10", "image": "https://launchercontent.mojang.com/v2/images/1.21.10540x540.jpg"}];

const state = {
  settings: {
    min_memory_mb: 512, max_memory_mb: 4096, auto_memory: true, render_scale: 100, java_path: null, extra_jvm_args: [],
    microsoft_client_id: null, download_concurrency: 32, show_snapshots: false,
    show_old_versions: false, sky: "cycle", day_length_min: 10, ui_sounds: true, animations: true,
    game_width: null, game_height: null, fullscreen: false, after_launch: "keep", vortex_mods: true, performance_mods: true, open_log_on_launch: false,
    narrator_off: true, skip_multiplayer_warning: true, hide_tutorial: true, max_fps_mode: false, game_options: {}, gpu_high_performance: false, process_priority: "normal", prefer_ipv4: false, track_playtime: true, gc: "auto", backups_keep: 5, crash_helper: true, log_retention_days: 30,
    backup_worlds: false, auto_update_mods: false, rejoin_last_server: false, pre_launch_command: "", post_exit_command: "", wrapper_command: "", game_env: "",
    accent: "amber", glass: 60, logo_style: 0, background: "golden", theme: "dark", vortex_menu: true, show_player: true, world_life: true, clock_12h: false,
    mods_allow_unstable: false, mods_with_dependencies: true, fast_launch: true, tuned_jvm: true,
  },
  skins: [],
  applied: null,
  cape: "vortex",
  installed: {},
  accounts: [{ uuid: "5627dd98e6be3c21b8a8e92344183641", username: "Steve", kind: "offline", selected: true }],
  instances: [],
  mods: {},
  running: new Map(),
};

function versionList() {
  const ids = [
    ["26.4-snapshot-6", "snapshot", "2026-10-27"], ["26.3", "release", "2026-09-15"], ["26.2", "release", "2026-06-16"],
    ["26.1.2", "release", "2026-04-09"], ["26.1", "release", "2026-03-24"], ["1.21.11", "release", "2025-12-09"],
    ["1.21.10", "release", "2025-10-07"], ["1.21.4", "release", "2024-12-03"], ["1.21.1", "release", "2024-08-08"],
    ["1.20.4", "release", "2023-12-07"], ["1.20.1", "release", "2023-06-12"], ["1.19.4", "release", "2023-03-14"],
    ["1.18.2", "release", "2022-02-28"], ["1.16.5", "release", "2021-01-14"], ["1.12.2", "release", "2017-09-18"],
    ["1.8.9", "release", "2015-12-09"], ["1.7.10", "release", "2014-06-26"], ["1.6.4", "release", "2013-09-19"],
    ["b1.7.3", "beta", "2011-07-08"], ["b1.8.1", "beta", "2011-09-19"], ["a1.2.6", "alpha", "2010-12-03"],
    ["a1.0.4", "alpha", "2010-07-09"], ["rd-132211", "alpha", "2009-05-13"],
  ];
  return { latestRelease: "26.3", latestSnapshot: "26.4-snapshot-6", versions: ids.map(([id, kind, date]) => ({ id, kind, date })) };
}

function view(i) {
  return { ...i, running: state.running.has(i.name) };
}

// The same sets the launcher installs (Fabric), so the demo shows the real list.
const PERF = ["Sodium", "Lithium", "FerriteCore", "EntityCulling", "ImmediatelyFast", "ModernFix", "More Culling", "Dynamic FPS", "BadOptimizations", "Krypton", "ScalableLux", "Memory Leak Fix", "Fast IP Ping", "Debugify", "Sodium Extra", "Reese's Sodium Options", "Cull Less Leaves", "FastQuit", "Clumps", "ThreadTweak", "Language Reload"];
const PACK_MODS = {
  performance: PERF,
  normal: [...PERF, "Mod Menu", "Iris Shaders", "Continuity", "LambDynamicLights", "AppleSkin", "Xaero's Minimap", "Xaero's World Map", "JEI", "Jade", "Mouse Tweaks", "Shulker Box Tooltip", "Chat Heads", "3D Skin Layers", "Entity Model Features", "Entity Texture Features", "BetterF3", "Controlling", "No Chat Reports", "Inventory Profiles Next", "Durability Tooltip", "Falling Leaves", "Wavey Capes", "Item Highlighter", "Bobby", "Not Enough Animations", "Sound Physics Remastered", "Presence Footsteps", "AmbientSounds", "Enchantment Descriptions", "Visuality", "Cubes Without Borders", "Particle Rain", "Paginated Advancements", "Make Bubbles Pop", "Traveler's Titles", "Better Clouds", "Model Fix", "Capes", "Better Mount HUD", "Screenshot to Clipboard"],
  pvp: [...PERF, "Mod Menu", "3D Skin Layers", "Wavey Capes", "Chat Heads", "No Chat Reports", "Better Ping Display", "Shield Statuses", "Status Effect Bars", "Durability Tooltip", "Item Highlighter", "Auth Me", "Bobby", "Capes", "Controlling", "Mouse Tweaks", "Visuality"],
};
// Alternatives that stand in when the first choice has no build, like the real thing.
const STAND_IN = { "JEI": "REI" };
// No 1.21.4 build of these.
const NO_BUILD = new Set(["Memory Leak Fix", "Cull Less Leaves"]);

async function installPack(inst) {
  const name = inst.name;
  const mods = [...new Set(PACK_MODS[inst.pack])].map((m) => ({ id: m.toLowerCase().replace(/[^a-z0-9]+/g, "-"), name: m }));
  const version = `${inst.minecraftVersion} ${{ fabric: "Fabric", forge: "Forge" }[inst.loader] || ""}`.trim();
  emit("pack-install", { instance: name, phase: "start", pack: inst.pack, version, mods });
  await wait(900);
  let added = 0, skipped = 0;
  for (const m of mods) {
    emit("pack-install", { instance: name, phase: "mod", id: m.id, state: "active" });
    emit("launch-progress", { instance: name, stage: `Adding ${m.name}`, done: 0, total: 0 });
    const size = 200_000 + Math.random() * 1_400_000;
    const steps = 3 + Math.floor(Math.random() * 3);
    for (let i = 1; i <= steps; i++) {
      await wait(60 + Math.random() * 70);
      if (!NO_BUILD.has(m.name)) emit("launch-progress", { instance: name, stage: `Downloading ${m.name}`, done: Math.round((size * i) / steps), total: Math.round(size) });
    }
    const st = NO_BUILD.has(m.name) ? "skipped" : "added";
    if (st === "added") added++; else skipped++;
    emit("pack-install", { instance: name, phase: "mod", id: m.id, state: st, ...(STAND_IN[m.name] ? { name: STAND_IN[m.name] } : {}) });
    await wait(60);
  }
  emit("pack-install", { instance: name, phase: "done", added, skipped, failed: 0 });
  inst.modCount += added;
  emit("game-log", { instance: name, lines: [{ level: "info", text: `Mods added: ${mods.filter((m) => !NO_BUILD.has(m.name)).map((m) => m.name).join(", ")}.` }] });
  await wait(1400);
}

async function simulateLaunch(name) {
  const inst = state.instances.find((i) => i.name === name);
  emit("game-state", { instance: name, state: "preparing" });
  state.fast ||= new Set();
  const fast = state.fast.has(name);
  state.fast.add(name);
  // First launch of a PvP / Normal / Performance instance: its mods arrive.
  if (inst.packPending && PACK_MODS[inst.pack]) {
    inst.packPending = false;
    await installPack(inst);
  }
  const stages = fast ? [["Fast launch", 1, 1, 350], ["Ready", 1, 1, 150]] : [
    ["Resolving " + inst.minecraftVersion, 0, 0, 500],
    ["Libraries", 31, 31, 900],
    ["Assets", 734, 734, 1600],
    ["Checking Java runtime", 0, 0, 500],
    ["Ready", 1, 1, 300],
  ];
  for (const [stage, total, , ms] of stages) {
    if (!state.running.has(name)) return;
    const steps = Math.max(1, Math.min(12, total));
    for (let s = 0; s <= steps; s++) {
      emit("launch-progress", { instance: name, stage, done: Math.round((s / steps) * total), total });
      await wait(ms / (steps + 1));
    }
  }
  if (!state.running.has(name)) return;
  emit("game-state", { instance: name, state: "running" });
  const lines = [
    ["info", "[Render thread/INFO] Setting user: Steve"],
    ["info", "[Render thread/INFO] Backend library: LWJGL version 3.3.3"],
    ["warn", "[Render thread/WARN] Offline account: singleplayer and LAN only."],
    ["info", "[Render thread/INFO] Reloading ResourceManager: vanilla"],
    ["info", "[Render thread/INFO] Created: 1024x512x4 minecraft:textures/atlas/blocks.png-atlas"],
    ["info", "[Render thread/INFO] Sound engine started"],
  ];
  for (const [level, text] of lines) {
    if (!state.running.has(name)) return;
    await wait(350);
    emit("game-log", { instance: name, lines: [{ level, text }] });
  }
}

function findHost(name) {
  const h = (state.hosts || []).find((x) => x.name === name);
  if (!h) throw `no server called ${name}`;
  return h;
}

function hostView(h) {
  return { name: h.name, kind: h.kind, version: h.version, memoryMb: h.memoryMb, created: h.created, backupOnStop: h.backupOnStop, restartOnCrash: h.restartOnCrash, keepBackups: h.keepBackups, publicAddress: h.publicAddress, jvmArgs: h.jvmArgs, running: h.running, starting: h.starting, players: h.players, startedAt: h.startedAt, port: Number(h.props["server-port"]) || 25565, maxPlayers: Number(h.props["max-players"]) || 20, motd: h.props.motd, lanAddress: "192.168.1.24" };
}

function libView() {
  if (!state.seeded && globalThis.__vortexSkinSeeds) {
    state.seeded = true;
    globalThis.__vortexSkinSeeds().forEach(([name, dataUrl, slim], i) => state.skins.push({ id: `seed${i}`, name, slim, added: now, dataUrl }));
    state.applied = "seed0";
  }
  return { skins: state.skins, applied: state.applied, cape: state.cape, capes: ["none", "vortex", "diamond", "ember", "emerald", "amethyst"], savedCapes: (state.savedCapes ||= []), microsoft: false };
}

export const mock = {
  listen(event, handler) {
    if (!listeners.has(event)) listeners.set(event, []);
    listeners.get(event).push(handler);
    return Promise.resolve(() => {});
  },
  async invoke(cmd, args) {
    await wait(60 + Math.random() * 120);
    switch (cmd) {
      case "bootstrap":
        return { appVersion: "0.11.0", dataDir: "%APPDATA%\\VortexClient", platform: "windows", settings: { ...state.settings }, accounts: state.accounts, instances: state.instances.map(view), systemRamMb: 8192, cpuCount: 4, autoMemoryMb: 2560, autoMemoryModdedMb: 3072 };
      case "list_versions":
        return versionList();
      case "loader_versions": {
        if (args.loader === "fabric") {
          const ok = !/^(1\.[0-9]\.|1\.1[0-3]|b|a|rd)/.test(args.game);
          return ok ? ["0.20.1", "0.20.0", "0.19.2"].map((v, i) => ({ id: v, display: v, stable: true, recommended: i === 0, latest: i === 0 })) : [];
        }
        if (args.loader === "forge") {
          const ok = /^1\.(6|7|8|9|1[0-9]|2[0-1])/.test(args.game);
          return ok ? ["47.4.28", "47.4.20", "47.4.10"].map((v, i) => ({ id: v, display: v, stable: true, recommended: i === 0, latest: i === 0 })) : [];
        }
        return [];
      }
      case "list_instances":
        return state.instances.map(view);
      case "create_instance": {
        if (state.instances.some((i) => i.name.toLowerCase() === args.name.toLowerCase())) throw `an instance called '${args.name}' already exists`;
        const labels = { vanilla: "Vanilla", fabric: "Fabric", forge: "Forge" };
        const inst = { name: args.name, minecraftVersion: args.version, loader: args.loader, loaderVersion: args.loaderVersion || null, loaderLabel: labels[args.loader] + (args.loaderVersion ? " " + args.loaderVersion : ""), memoryMb: args.memoryMb || null, joinServer: null, jvmArgs: "", lastPlayed: null, gameDir: "", running: false, modCount: 0, pack: args.pack || null, packPending: !!args.pack && args.pack !== "vanilla" };
        state.instances.push(inst);
        return view(inst);
      }
      case "update_instance": {
        const inst = state.instances.find((i) => i.name === args.name);
        inst.memoryMb = args.memoryMb ?? null;
        inst.joinServer = args.joinServer || null;
        if (args.jvmArgs != null) inst.jvmArgs = args.jvmArgs;
        return view(inst);
      }
      case "delete_instance":
        state.instances = state.instances.filter((i) => i.name !== args.name);
        return state.instances.map(view);
      case "launch_instance": {
        if (!state.accounts.some((a) => a.selected)) throw "add an account first (Accounts page)";
        state.running.set(args.name, true);
        const inst = state.instances.find((i) => i.name === args.name);
        inst.lastPlayed = Math.floor(Date.now() / 1000);
        simulateLaunch(args.name);
        return null;
      }
      case "server_status": {
        const known = { "mc.hypixel.net": [41237, 200000, 28], "minemen.club": [2384, 10000, 61], "play.cubecraft.net": [6120, 30000, 44], "play.wynncraft.com": [1873, 5000, 72], "play.mccisland.net": [3410, 20000, 39] };
        await wait(300 + Math.random() * 700);
        const k = known[args.address];
        if (!k) throw new Error(`${args.address} didn't answer`);
        return { address: args.address, online: k[0], max: k[1], motd: "A Minecraft server", version: "1.8-1.21", icon: null, pingMs: k[2] };
      }
      case "check_update":
        return location.search.includes("update")
          ? { available: true, current: "0.11.0", version: "0.11.1", published: "2026-11-12", size: 10_700_000, url: "https://github.com/x/y/releases/download/vortex-v0.11.1/Vortex.exe",
              notes: "## What's new\n- Faster instance switching\n- New Servers panel on Home\n- Smoother pop-up animations\n- Fixed the Stop Game button" }
          : { available: false, current: "0.11.0", version: "0.11.0" };
      case "install_update":
        for (let i = 0; i <= 20; i++) { emit("update-progress", { stage: "download", done: i * 535000, total: 10_700_000 }); await wait(90); }
        emit("update-progress", { stage: "install", done: 1, total: 1 });
        await wait(500);
        emit("update-progress", { stage: "restart", done: 1, total: 1 });
        return null;
      case "warm_up":
        return 0;
      case "verify_instance":
        state.fast?.delete(args.name);
        return null;
      case "stop_instance":
        if (state.running.delete(args.name)) {
          setTimeout(() => emit("game-state", { instance: args.name, state: "exited", code: 0 }), 300);
        }
        return null;
      case "add_offline_account": {
        state.accounts.forEach((a) => (a.selected = false));
        state.accounts.push({ uuid: Math.random().toString(16).slice(2).padEnd(32, "0"), username: args.username, kind: "offline", selected: true });
        return state.accounts;
      }
      case "remove_account":
        state.accounts = state.accounts.filter((a) => a.uuid !== args.uuid);
        if (state.accounts.length && !state.accounts.some((a) => a.selected)) state.accounts[0].selected = true;
        return state.accounts;
      case "select_account":
        state.accounts.forEach((a) => (a.selected = a.uuid === args.uuid));
        return state.accounts;
      case "microsoft_login":
        throw "set your Microsoft (Azure) client ID in Settings first — see the README";
      case "save_settings":
        state.settings = { ...args.settings };
        return state.settings;
      case "music_library":
        return { dir: "C:\\Users\\You\\Music", tracks: [] };
      case "music_lyrics": {
        // In the preview: LRCLIB straight from the page (if the page may reach it).
        const q = new URLSearchParams({ artist_name: args.artist || "", track_name: args.title || "" });
        try {
          const r = await fetch(`https://lrclib.net/api/get?${q}`);
          if (!r.ok) return { synced: [], plain: null, source: "" };
          const j = await r.json();
          const synced = (j.syncedLyrics || "").split("\n").map((l) => l.match(/^\[(\d+):(\d+(?:\.\d+)?)\]\s*(.*)$/)).filter(Boolean).map((m) => [Math.round((Number(m[1]) * 60 + Number(m[2])) * 1000), m[3]]);
          return { synced, plain: j.plainLyrics || null, source: "LRCLIB" };
        } catch { return { synced: [], plain: null, source: "" }; }
      }
      case "music_radio":
        try {
          const q = new URLSearchParams({ name: args.query || "", tag: args.tag || "", limit: "40", hidebroken: "true", order: "clickcount", reverse: "true" });
          const r = await fetch(`https://de1.api.radio-browser.info/json/stations/search?${q}`);
          return (await r.json()).filter((s) => s.url_resolved).map((s) => ({ name: s.name, url: s.url_resolved, favicon: s.favicon, tags: s.tags, country: s.country, codec: s.codec, bitrate: s.bitrate }));
        } catch { return []; }
      case "music_trending":
      case "music_search":
      case "music_playlists":
      case "music_playlist": {
        const A = "https://api.audius.co/v1";
        const art = (v) => v.artwork?.["480x480"] || v.artwork?.["150x150"] || "";
        const song = (t) => (t.is_streamable === false || t.stream_conditions ? null : { id: t.id, title: (t.title || "").trim(), artist: t.user?.name || "", artwork: art(t), duration: t.duration || 0, genre: t.genre || "", url: `${A}/tracks/${t.id}/stream?app_name=VortexClient` });
        const get = async (path, q = {}) => { const r = await fetch(`${A}${path}?${new URLSearchParams({ app_name: "VortexClient", ...q })}`); if (!r.ok) throw new Error(`Audius said ${r.status}`); return (await r.json()).data || []; };
        if (cmd === "music_trending") return (await get("/tracks/trending", { time: args.time || "week", limit: "50", ...(args.genre ? { genre: args.genre } : {}) })).map(song).filter(Boolean);
        if (cmd === "music_search") return (await get("/tracks/search", { query: args.query, limit: "50" })).map(song).filter(Boolean);
        if (cmd === "music_playlist") return (await get(`/playlists/${args.id}/tracks`)).map(song).filter(Boolean);
        const pl = args.query ? await get("/playlists/search", { query: args.query, limit: "20" }) : await get("/playlists/trending", { limit: "20" });
        return pl.map((p) => ({ id: p.id, name: (p.playlist_name || "").trim(), owner: p.user?.name || "", artwork: art(p), count: p.track_count || 0 }));
      }
      case "host_list":
        return (state.hosts ||= []).map(hostView);
      case "host_versions":
        await wait(300);
        return ["1.21.11", "1.21.10", "1.21.8", "1.21.4", "1.21.1", "1.20.6", "1.20.4", "1.20.1", "1.19.4", "1.18.2", "1.16.5", "1.12.2", "1.8.9"];
      case "host_create": {
        if (!args.eula) throw "accept the Minecraft EULA to host a server";
        const name = (args.config.name || "").replace(/[^A-Za-z0-9 _.-]/g, "").trim();
        if (!name) throw "give the server a name";
        if ((state.hosts ||= []).some((h) => h.name === name)) throw `there's already a server called ${name}`;
        for (let i = 1; i <= 10; i++) { await wait(120); emit("server-progress", { name, stage: "Downloading the server", done: i * 5e6, total: 5e7 }); }
        const h = { name, kind: args.config.kind, version: args.config.version, memoryMb: args.config.memoryMb, created: Date.now() / 1000, backupOnStop: false, restartOnCrash: false, keepBackups: 10, publicAddress: "", jvmArgs: "", running: false, starting: false, players: [], startedAt: 0, log: [], backups: [], addons: [], ops: [], whitelist: [], banned: [], bannedIps: [],
          props: { motd: `${name} §7· hosted with Vortex`, "server-port": "25565", "max-players": "20", "view-distance": "10", "simulation-distance": "8", difficulty: "normal", gamemode: "survival", "online-mode": "true", pvp: "true", "white-list": "false", "spawn-protection": "16", "level-name": "world", "enable-command-block": "false", "allow-flight": "false" } };
        state.hosts.unshift(h);
        return hostView(h);
      }
      case "host_update": {
        const h = findHost(args.name);
        Object.assign(h, args.patch);
        return hostView(h);
      }
      case "host_delete":
        state.hosts = state.hosts.filter((h) => h.name !== args.name);
        return null;
      case "host_start": {
        const h = findHost(args.name);
        h.running = true; h.starting = true; h.startedAt = Date.now() / 1000; h.log = [];
        const say = (t, d) => setTimeout(() => { if (!h.running) return; h.log.push(t); emit("server-log", { name: h.name, line: t }); }, d);
        say(`[Vortex] Starting ${h.kind} ${h.version} with ${h.memoryMb} MB of memory`, 0);
        say("[12:00:01] [main/INFO]: Loading properties", 300);
        say("[12:00:02] [Server thread/INFO]: Starting minecraft server version " + h.version, 600);
        say("[12:00:02] [Server thread/INFO]: Default game type: SURVIVAL", 800);
        say("[12:00:03] [Server thread/INFO]: Preparing level \"world\"", 1100);
        say("[12:00:05] [Server thread/INFO]: Preparing spawn area: 83%", 1700);
        setTimeout(() => { h.starting = false; say("[12:00:06] [Server thread/INFO]: Done (4.215s)! For help, type \"help\"", 0); emit("server-state", { name: h.name }); }, 2200);
        setTimeout(() => { if (!h.running) return; h.players = ["Steve", "Alex_Builds"]; say("[12:01:10] [Server thread/INFO]: Steve joined the game", 0); say("[12:01:15] [Server thread/INFO]: Alex_Builds joined the game", 50); emit("server-state", { name: h.name }); }, 3500);
        emit("server-state", { name: h.name });
        return null;
      }
      case "host_stop": {
        const h = findHost(args.name);
        const line = (t) => { h.log.push(t); emit("server-log", { name: h.name, line: t }); };
        line("[12:30:00] [Server thread/INFO]: Stopping the server");
        setTimeout(() => { h.running = false; h.players = []; line("[Vortex] The server stopped (exit code 0)"); emit("server-state", { name: h.name }); }, 900);
        return null;
      }
      case "host_command": {
        const h = findHost(args.name);
        if (!h.running) throw "the server isn't running";
        const t = `[12:10:00] [Server thread/INFO]: ${args.line === "list" ? `There are ${h.players.length} of a max of 20 players online: ${h.players.join(", ")}` : `Ran: ${args.line}`}`;
        h.log.push(t);
        emit("server-log", { name: h.name, line: t });
        return null;
      }
      case "host_log":
        return findHost(args.name).log.slice();
      case "host_properties":
        return Object.entries(findHost(args.name).props);
      case "host_set_properties":
        Object.assign(findHost(args.name).props, args.changes);
        return null;
      case "host_players": {
        const h = findHost(args.name);
        return { online: h.players, ops: h.ops, whitelist: h.whitelist, banned: h.banned, bannedIps: h.bannedIps, whitelistOn: h.props["white-list"] === "true" };
      }
      case "host_player": {
        const h = findHost(args.name);
        const p = args.player;
        const lists = { op: ["ops", 1], deop: ["ops", 0], "whitelist-add": ["whitelist", 1], "whitelist-remove": ["whitelist", 0], ban: ["banned", 1], pardon: ["banned", 0], "ban-ip": ["bannedIps", 1], "pardon-ip": ["bannedIps", 0] };
        if (args.action === "whitelist-on" || args.action === "whitelist-off") { h.props["white-list"] = String(args.action === "whitelist-on"); return null; }
        if (args.action === "kick") { h.players = h.players.filter((x) => x !== p); emit("server-state", { name: h.name }); return null; }
        const [key, add] = lists[args.action];
        const field = key === "bannedIps" ? "ip" : "name";
        h[key] = h[key].filter((e) => e[field].toLowerCase() !== p.toLowerCase());
        if (add) h[key].push(key === "bannedIps" ? { ip: p } : { name: p, uuid: "", level: key === "ops" ? 4 : undefined, reason: args.reason || undefined });
        if (args.action === "ban") h.players = h.players.filter((x) => x !== p);
        return null;
      }
      case "host_backups":
        return findHost(args.name).backups;
      case "host_backup": {
        await wait(900);
        const h = findHost(args.name);
        const b = { file: `${h.name}-${Date.now()}.zip`, size: 48e6 + Math.random() * 2e7, time: Date.now() / 1000 };
        h.backups.unshift(b);
        return b;
      }
      case "host_restore":
        await wait(600);
        return null;
      case "host_delete_backup": {
        const h = findHost(args.name);
        h.backups = h.backups.filter((b) => b.file !== args.file);
        return null;
      }
      case "host_addons":
        return findHost(args.name).addons;
      case "host_addon_search": {
        const h = findHost(args.name);
        const facets = JSON.stringify(h.kind === "fabric" ? [["project_type:mod"], ["categories:fabric"], [`versions:${h.version}`], ["server_side:required", "server_side:optional"]] : [["project_type:plugin"], ["categories:paper", "categories:spigot", "categories:bukkit"], [`versions:${h.version}`]]);
        try {
          const r = await fetch(`https://api.modrinth.com/v2/search?${new URLSearchParams({ query: args.query || "", facets, limit: "30", index: args.query ? "relevance" : "downloads" })}`);
          return (await r.json()).hits.map((x) => ({ id: x.project_id, title: x.title, description: x.description, icon: x.icon_url || "", downloads: x.downloads, author: x.author }));
        } catch { return []; }
      }
      case "host_addon_install": {
        await wait(700);
        const h = findHost(args.name);
        const f = `${args.project}.jar`;
        if (!h.addons.some((a) => a.file === f)) h.addons.push({ file: f, size: 1.2e6, enabled: true });
        return [f];
      }
      case "host_addon_toggle": {
        const h = findHost(args.name);
        if (args.enabled == null) h.addons = h.addons.filter((a) => a.file !== args.file);
        else { const a = h.addons.find((x) => x.file === args.file); if (a) { a.enabled = args.enabled; a.file = args.enabled ? a.file.replace(/\.disabled$/, "") : `${a.file}.disabled`; } }
        return null;
      }
      case "host_tunnel":
        throw "the preview can't open programs; in the app this opens playit.gg's agent";
      case "host_folder":
        return null;
      case "music_state":
        return null;
      case "music_commands":
        return [];
      case "news":
        return [
          { title: "Minecraft 26.4 Snapshot 6", version: "26.4-snapshot-6", kind: "snapshot", image_url: null, date: "2026-10-27", summary: "A new snapshot is out with fixes and tweaks." },
          { title: "Minecraft 26.3", version: "26.3", kind: "release", image_url: null, date: "2026-09-15", summary: "The latest release of Minecraft: Java Edition." },
          { title: "Minecraft 26.2", version: "26.2", kind: "release", image_url: null, date: "2026-06-16", summary: "Another update for Java Edition." },
        ];
      case "list_mods":
        if (args.kind === "resourcepack") return ((state.packs ||= {})[args.name] || []);
        return (args.kind && args.kind !== "mod") ? [] : (state.mods[args.name] || []);
      case "active_packs":
        return ((state.activePacks ||= {})[args.name] || []);
      case "set_active_packs": {
        const have = ((state.packs ||= {})[args.name] || []).map((p) => p.file);
        return ((state.activePacks ||= {})[args.name] = args.files.filter((f) => have.includes(f)));
      }
      case "toggle_mod": {
        const m = (state.mods[args.name] || []).find((x) => x.file === args.file);
        if (m) {
          m.enabled = args.enabled;
          m.file = args.enabled ? m.file.replace(/\.disabled$/, "") : m.file + ".disabled";
        }
        return state.mods[args.name] || [];
      }
      case "delete_mod":
        if (args.kind === "resourcepack") return ((state.packs ||= {})[args.name] = (state.packs[args.name] || []).filter((x) => x.file !== args.file));
        state.mods[args.name] = (state.mods[args.name] || []).filter((x) => x.file !== args.file);
        return state.mods[args.name];
      case "player_skin": {
        const chosen = state.skins.find((x) => x.id === state.applied);
        const capeUrl = (state.savedCapes || []).find((c) => c.id === state.cape)?.dataUrl || null;
        return chosen ? { dataUrl: chosen.dataUrl, slim: chosen.slim, source: "chosen", cape: state.cape, capeUrl } : { dataUrl: null, slim: false, source: "none", cape: state.cape, capeUrl };
      }
      case "modrinth_search": {
        await wait(250);
        const all = MODRINTH[args.kind] || [];
        const q = (args.query || "").toLowerCase();
        let list = all.filter((h) => !q || h.title.toLowerCase().includes(q) || h.description.toLowerCase().includes(q));
        if (args.sort === "follows") list = [...list].sort((a, b) => b.follows - a.follows);
        if (args.sort === "updated" || args.sort === "newest") list = [...list].sort((a, b) => b.updated.localeCompare(a.updated));
        const off = args.offset || 0;
        return { hits: list.slice(off, off + 24), offset: off, total: list.length };
      }
      case "modrinth_project": {
        const h = Object.values(MODRINTH).flat().find((x) => x.id === args.id) || MODRINTH.mod[0];
        const inst = state.instances.find((i) => i.name === args.instance);
        const gv = inst ? inst.minecraftVersion : "1.21.4";
        return {
          project: { ...h, kind: args.kind, follows: h.follows, license: "MIT", pageUrl: `https://modrinth.com/${args.kind}/${h.slug}`, sourceUrl: null, wikiUrl: null, discordUrl: null, gallery: h.gallery ? [{ url: h.gallery, title: null }] : [] },
          versions: [0, 1, 2, 3].map((i) => ({ id: `v${i}`, name: `${h.title} ${3 - i}.${i}.0`, number: `${3 - i}.${i}.0`, kind: i === 1 ? "beta" : "release", gameVersions: [gv], loaders: [inst?.loader || "fabric"], date: `2026-0${9 - i}-1${i}`, downloads: 100000 * (4 - i), file: `${h.slug}-${3 - i}.${i}.0.jar`, size: 1200000 })),
        };
      }
      case "modrinth_install": {
        await wait(900);
        const per = (state.installed[args.instance] ||= {});
        (per[args.kind] ||= []).push(args.projectId);
        const h = Object.values(MODRINTH).flat().find((x) => x.id === args.projectId);
        if (args.kind === "resourcepack" && h) {
          ((state.packs ||= {})[args.instance] ||= []).push({ file: `${h.slug}.zip`, enabled: true, size: 4000000, name: h.title, version: null, description: h.description, loader: "unknown", iconUrl: h.iconUrl, projectId: h.id });
        }
        if (args.kind === "mod" && h) {
          (state.mods[args.instance] ||= []).push({ file: `${h.slug}.jar`, enabled: true, size: 900000, name: h.title, version: "1.0.0", description: h.description, loader: "fabric", iconUrl: h.iconUrl, projectId: h.id });
        }
        return { installed: [h ? h.title : args.projectId], already: [] };
      }
      case "modrinth_installed":
        return state.installed[args.instance] || {};
      case "version_art":
        return ART;
      case "skin_library":
        return libView();
      case "skin_add": {
        const id = Math.random().toString(16).slice(2, 18);
        state.skins.push({ id, name: args.name || "Skin", slim: !!args.slim, added: now, dataUrl: args.dataUrl });
        return libView();
      }
      case "skin_from_player":
        throw "the preview can't reach Mojang; this works in the app";
      case "skin_update": {
        const sk = state.skins.find((x) => x.id === args.id);
        if (sk && args.name != null) sk.name = args.name;
        if (sk && args.slim != null) sk.slim = args.slim;
        return libView();
      }
      case "skin_remove":
        state.skins = state.skins.filter((x) => x.id !== args.id);
        if (state.applied === args.id) state.applied = null;
        return libView();
      case "skin_apply":
        state.applied = args.id || null;
        return libView();
      case "set_cape":
        state.cape = args.cape;
        return libView();
      case "skin_defaults":
        return [];
      case "cape_find": {
        const r = await fetch(`https://api.capes.dev/load/${encodeURIComponent(args.username)}`);
        if (!r.ok) throw "capes.dev didn't answer";
        const d = await r.json();
        const L = { minecraft: "Minecraft", optifine: "OptiFine", labymod: "LabyMod", minecraftcapes: "MinecraftCapes", "5zig": "5zig", tlauncher: "TLauncher", cloaksplus: "Cloaks+" };
        return Object.entries(d).filter(([, v]) => v && v.exists).map(([k, v]) => ({ service: L[k] || k, imageUrl: v.stillImageUrl || v.imageUrl })).filter((c) => c.imageUrl);
      }
      case "cape_add": {
        state.savedCapes ||= [];
        let c = state.savedCapes.find((x) => x.dataUrl === args.dataUrl);
        if (!c) { c = { id: `c${Math.random().toString(16).slice(2, 18).padEnd(16, "0")}`, name: args.name, source: args.source, dataUrl: args.dataUrl }; state.savedCapes.push(c); }
        if (args.wear) state.cape = c.id;
        return libView();
      }
      case "cape_remove":
        state.savedCapes = (state.savedCapes || []).filter((c) => c.id !== args.id);
        if (state.cape === args.id) state.cape = "none";
        return libView();
      case "import_mods":
        return state.mods[args.name] || [];
      case "install_status":
        return { supported: true, installed: !!state.installed, elsewhere: false, installDir: "C:\\Users\\You\\AppData\\Local\\Programs\\Vortex Client", runningFrom: "C:\\Users\\You\\Downloads\\Vortex.exe", autostart: !!state.autostart };
      case "install_app":
        await wait(1200);
        state.installed = true;
        return null;
      case "uninstall_app":
        state.installed = false;
        return null;
      case "set_autostart":
        state.autostart = !!args.on;
        return state.autostart;
      case "save_menu_art":
      case "open_path":
      case "open_url":
        return null;
      default:
        throw `unknown command ${cmd}`;
    }
  },
};
