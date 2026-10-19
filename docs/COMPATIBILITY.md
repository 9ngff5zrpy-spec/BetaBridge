# Experimental compatibility matrix

Built with OpenAI Codex and tested iteratively by a human on an iPhone and the original Java client. The release is a community preview. Manual confirmation means that feature worked in the local test setup, not that every edge case or hosting environment has been covered.

| Area | Current adaptation | Evidence / remaining limits |
|---|---|---|
| Connection | Geyser → ViaProxy/ViaVersion/ViaLegacy → original Beta 1.6.6 | Bedrock 26.52/protocol 2193; co-located server, macOS and iPhone tested |
| Crafting | Original 148 recipes, 56 in 2×2; result prediction; manual and automatic crafting | Matcher checked against original server; Bedrock autocrafting retained |
| Inventory | Cursor-click drops, authoritative held-slot responses after use | Use/drop/ghost-item corrections tested; more inventory edge cases welcome |
| Identity | Verified Xbox session → stable legacy name and saved player data | No Floodgate Java-account linking; private transport required |
| Skins | Export compatible standard skins for a LegacyFix-enabled Java client | Persona/custom geometry fallback; broader current-profile verification pending |
| Sleeping | Recognize Beta bed offsets and acknowledge without waking Bedrock | Manually confirmed |
| Furnace | Respect configured spawn protection without granting OP | Original failure and correction reproduced with original server |
| Fences | Full 1×1.5×1 Beta collision and correct connected/corner geometry | Manual confirmation plus all 16 visual connection masks checked |
| Redstone/minecarts | Translate controls once, acknowledge mounted position, fix powered-rail textures | Buttons/levers/repeaters and minecart behavior checked; original circuit/physics retained |
| Boat | Original-looking custom hull; original rider inputs; server buoyancy/collision; mount/exit correction | User confirmed boat works; destruction/stale-position kick reproduced and corrected |
| Fishing | Notify the real original catch window, play classic splash, show built-in particles | User confirmed catching works after string/Molang crash fix; RNG/reward unchanged |
| Jukebox | Original server decides insert/eject; server-confirmed 13/Cat playback | Basic use confirmed; exhaustive cross-device start/stop scenarios still pending |
| Chests | Static full cubes, all facings and double-chest face textures | User confirmed simple/double behavior; 72 faces match original client; official virtual-container fallback retained |
| Names | Localized chest and jukebox display names | Official name keys added after manual missing-name report |
| Texture/sound | Beta atlas and classic sounds, including old player hurt voice | Resource pack required; not every modern render effect is reproduced |
| Music | All overridden music events route to classic C418 playlist; discs 13 and Cat | Preserves Bedrock's music scheduling; does not force continuous playback |
| Biome matching | Beta regional grass/foliage palette via equivalent biome mapping; classic blue water | Representative regional colors, not exact continuous per-column Beta climate coloring |
| Fog | Air starts at 16 blocks and is opaque at 64; day/night phases, Nether profile | Bedrock lighting/sky blending/water fog are approximations |
| HUD | Hunger/XP hidden; hearts/armor/air moved closer to hotbar | Classic centred/touch layouts confirmed; other UI packs can conflict |
| Achievements | Not implemented | No official Xbox achievements or complete Beta progress emulation |
| VPS / dedicated hosting | Installation path documented; public gameplay not yet validated | Public Bedrock bind supported; Beta and proxy must share loopback/network namespace. Single-JAR-only panels need provider customization. Remote backends are rejected. |
| Future versions | Not guaranteed | Geyser 1262 internals and Beta JAR hashes are pinned |

The fixes span several layers. Sleeping, collision, movement, rendering and audio gaps should not all be described as upstream ViaLegacy bugs. Some fixes address modern Bedrock assumptions, some bridge omissions, and some regressions in earlier BetaBridge experiments.

The original Minecraft JAR is unchanged on disk. `Beta166ServerCompat` changes three behaviors in memory: configured spawn protection, a held-stack confirmation after use, and notification of actual fishing bites. It does not replace world generation, redstone, recipe validation or fish rewards.

The Xbox TU1 tutorial conversion and the separate Java ARM64/Metal client are not shipped in this release. The screenshots illustrate a demonstration world, not included content.
