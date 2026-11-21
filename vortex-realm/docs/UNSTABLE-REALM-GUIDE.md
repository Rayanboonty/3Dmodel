# The Unstable Realm: owner's guide (3.14.0)

Everything is **off** until you type `/realm start`. Installing the update changes nothing on the SMP.
Run the setup commands as an op; ops keep their admin rights inside the SMP.

## World: run the season in its own world (3.14.5)
1. Upload the world folder to the server, next to your other world folders (it must have a `level.dat` inside).
2. `/realm world` lists the world folders it can see.
3. `/realm world set <folder>` loads it and makes it the **Unstable Realm** world. It gets its own inventory, and a nether and End are made for it. The season then runs only there.
4. `/realm world tp` takes you there. Set the capital and place the NPCs in that world.
5. Until `/realm start`, only ops and preview testers can enter it. Everyone else gets "opens soon".
6. `/realm world clear` goes back to running the season in the normal SMP.

## 0. Test it alone first: preview mode (3.14.4)
`/realm preview` runs the **whole season only for you**. Everyone else on the SMP sees nothing: no NPCs, no fighters, no bars, no messages or titles, and no powers. Chapters, quests, choices, votes, events and the boss all work for you.
- `/realm preview add <player>` / `/realm preview remove <player>` adds or removes another tester (staff).
- Skip ahead while testing with `/realm chapter next`, and force any event with `/realm event riftnight|meteor|carnival|siege|finale`.
- `/realm preview off` ends the test. Then run **`/realm reset confirm`** to wipe the test characters and progress (NPC spots and the capital are kept), then `/realm start` for the real launch.
- Blocks are shared: the Rift Shrine (`/realm shrine`) and meteor explosions can be seen by others, so test somewhere quiet.

## 1. Setup (10 minutes)
1. Go to the place in the SMP overworld that will be the **capital** and type `/realm capital`.
2. Optional: `/realm shrine` builds the Rift Shrine (a 15x15 plaza with pillars) at the capital. This is where the final boss rises.
3. Place the 5 story characters where you want them (stand on the spot, then):
   - `/realm npc place mirel` (Oracle Mirel, the guide, secretly the one who broke the Crown)
   - `/realm npc place aldric` (Ser Aldric, Order of the Gate)
   - `/realm npc place vessa` (Vessa the Voice, Hollow Choir)
   - `/realm npc place brann` (Brann the Smith, knows the truth)
   - `/realm npc place pip` (Pip the Peddler, sells items for Rift Shards)
4. `/realm start` plays the opening cutscene to everyone in the SMP and opens `/character`.
5. Check everything with `/realm status`.

## 2. What players do
- `/character` picks one of 8 one-of-a-kind characters. Once taken, nobody else can have it.
- **Shift + F** (or `/ability`) uses their power. Passives and weaknesses run on their own.
- Each character has a personal **5-part story** with cutscenes, quests, choices and rewards. The purple boss bar shows the current quest.
- `/realm` shows status, `/realm journal` shows the quest log, `/realm vote` shows the server vote, `/realm who` lists who is who, `/realm replay` replays the last cutscene, `/realm buy` opens Pip's shop.
- NPCs remember players: what they say changes with reputation, faction, choices and the server's votes.

## 3. The story (5 chapters, advance automatically)
| Chapter | Name | Advances when |
|---|---|---|
| 1 | Arrival | 3 characters are picked |
| 2 | Factions | 150 Rift creatures are slain (vote: who holds the capital) |
| 3 | Betrayal | 4 bearers finish their chapter-3 quest (vote: forgive or banish Mirel) |
| 4 | War | 2 sieges are beaten |
| 5 | The Unmaking | the Unmaker rises at the capital 10 minutes after the chapter starts (vote: seal or claim the Rift, which decides the ending) |

Small server? Lower the targets in `plugins/VortexRealm/realm-story.yml` (`chapters.N.goal.target`) and type `/realm reload`, or push it forward with `/realm chapter next`.

## 4. World events (automatic)
- **Rift Night**: some nights the sky turns violet. Fighters triple, shards drop double, and survivors get shards at dawn.
- **Meteor**: lands at announced coordinates with loot and Star-touched Knight guards (from chapter 2).
- **Jester's Carnival**: 5 minutes of random blessings, fireworks and shard rain (from chapter 2).
- **Siege**: 3 waves attack the capital in chapter 4. Win and the chapter advances; lose and the capital is scarred.
- **The Unmaker**: the final boss, with 3 phases (knights, then enraged with shockwaves). The player who deals the most damage gets the ending title.
- Force any event with `/realm event riftnight|meteor|carnival|siege|finale`, and stop them with `/realm event stop`.

## Companions (3.14.7)
- `/companion` summons your sidekick, `/companion name <name>` renames it, `/companion dismiss` sends it home.
- Give it orders in chat, starting with its name: "Bran, follow me", "guard here", "defend me", "attack that" (what you're looking at), "attack Steve", "go there", "stay", "be peaceful" / "be aggressive", "go home".
- Talking to your companion is private: only you and players within 24 blocks see it.
- **With an AI key set in `/ai`** (Claude, OpenAI, Groq, a local Ollama...), it understands anything you say and answers in character. It knows the chapter, your character, its health and what's nearby. The AI can only pick from the companion's actions, never commands.
- If it dies, you can summon it again after 2 minutes. It never attacks its owner.
- Voice orders (Simple Voice Chat plus speech-to-text) aren't in yet.

## 5. AI fighters
Rift Stalkers, Hollow Archers, Rift Knights, Mercenary Duelists and Rift Brutes come out at night. They get tougher each chapter, match their target's armor, taunt in chat, and call nearby kin when one of them is hit. Kill too many and a **Rift Avenger** hunts that player by name.

## 6. Admin commands
`/realm start | stop | status | chapter next|<1-5> | capital | shrine | npc place|remove|list <id> | event <name> | release <player> | counter <name> <n> | reload | reset confirm`

## 7. Writing your own story
All text (cutscenes, quests, choices, NPC lines, endings, shop) is in `realm-story.yml`, with the format explained at the top of the file. Edit it and run `/realm reload`. Voice lines are planned for later.
