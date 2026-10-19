# BetaBridge

**Play on a Java Beta 1.6.6 server from Minecraft Bedrock, with classic textures and sounds, C418-only music, and biome-matched colors.**

**Experimental community project. Built with OpenAI Codex**, through iterative development and hands-on Java/iPhone testing. This is a free, unofficial compatibility project, not a stable or universally tested replacement for the original client.

Bedrock players use the **unmodified game**. No client mods, custom launcher or manual add-on installation are required—just accept the resource pack offered automatically when joining.

![BetaBridge running on an unmodified iPhone Bedrock client](docs/screenshots/01-sunset-dock.png)

**[Download the experimental release](https://github.com/9ngff5zrpy-spec/BetaBridge/releases/tag/v0.3.8-alpha.1)** · [Installation](docs/INSTALL.md) · [Hosting](docs/HOSTING.md) · [Screenshots](docs/GALLERY.md)

## How it works

`Unmodified Bedrock client → Geyser → ViaProxy / ViaVersion / ViaLegacy → original Java Beta 1.6.6 server`

BetaBridge adds compatibility fixes and Beta-specific adaptations on top of those projects. The original server controls world state, crafting, item quantities, redstone, vehicles and fishing rewards. Our small Java agent adds three runtime compatibility adjustments: configured spawn protection, authoritative held-slot updates, and real fishing-bite notifications. **The original Minecraft server JAR is not overwritten**, but its loaded code is adapted in memory.

## Features

- Original crafting recipes: 148 total, including 56 available in the personal 2×2 grid. Bedrock's automatic crafting is retained.
- Inventory synchronization, item consumption and inventory dropping adaptations.
- Stable Xbox-authenticated Bedrock identities, readable legacy names and player-data preservation; limited standard-skin conversion support.
- Sleeping and the original server's night skip, without immediately waking the Bedrock player.
- Furnace access respecting configured spawn protection, without granting operator permission.
- Classic fence models, corner connections and original Beta collision dimensions.
- Buttons, levers and repeaters, including interactions while riding a minecart; corrected powered-rail textures.
- Original-looking boats, server-authoritative controls, mounting/dismounting and recovery after boat destruction.
- Server-confirmed fishing bites, splash cues and original-server fish collection.
- Jukebox interactions and original discs 13/Cat, driven by server-confirmed playback state.
- Static, full-block Beta chests, including double-chest textures, and translated block names.
- Classic textures and sounds, the old player hurt sound, **C418-only background music**, and classic water blue.
- Regional grass/foliage tint matching through equivalent biomes; air fog starts at 16 blocks and ends at 64, with day/night color changes.
- Hunger/experience hidden, sprint disabled through the legacy configuration, and a compact classic hearts/hotbar layout.

Read the [compatibility matrix](docs/COMPATIBILITY.md) for what is tested, approximate or still pending. These fixes span the whole compatibility stack; they are not all bugs in ViaLegacy itself.

## Install on a Java Beta 1.6.6 server

Run **the bridge and original Beta server on the same VPS, dedicated server or compatible hosting container**. The internal Java listeners stay on loopback; Bedrock players connect to your host's public address. A host must allow JDK 21, Python and two Java processes in the same network namespace. A panel limited to one custom JAR needs a custom startup configuration; uploading these files to a Paper plugins folder will not work. See the [hosting guide](docs/HOSTING.md).

1. Stop your existing server. Install **JDK 21** and **Python 3.9+** on the host. Java 21 is required by the modern bridge stack; original Minecraft Beta itself does not require that version. The installer uses it for both processes.
2. Extract the **community-preview ZIP into your server folder**, beside its original Beta 1.6.6 JAR and `server.properties`. One enclosing `BetaBridge-...` folder is detected automatically; you do not need to rearrange it.
3. Run **Install-BetaBridge.command** on macOS or **Install-BetaBridge.bat** on Windows. On Linux/a hosting terminal:

   ```sh
   python3 install.py
   ```

   It finds the original server JAR and configured world, downloads and verifies the two official dependencies, preserves normal server settings and creates a separate `betabridge-runtime` installation. Your original world stays intact. A configured world that cannot be found stops installation instead of silently creating a replacement.

4. Start with the generated **Start-BetaBridge.command**, **Start-BetaBridge.bat**, or:

   ```sh
   python3 Start-BetaBridge.py
   ```

   A compatible hosting panel can use that same Python command as its startup command. Use this launcher from now on: the active world is `betabridge-runtime/beta/world`.

5. Allocate/open **TCP + UDP 19132** and **UDP 19133** on your host. Bedrock players enter your host's public IP/domain and port **19132**, sign in to Xbox, and accept the automatic pack. **No Bedrock mods or manual add-on installation.**

Hosting provider assigned different ports? Run installation with `python3 install.py --bedrock-port 20000 --nethernet-port 20001`, replacing these examples with the actual allocations. Do not expose the two internal Java TCP ports. For LAN testing, players use the server's LAN address.

Type `stop` or press Ctrl+C in the runner to save and stop. Your original files are preserved, but new progress belongs to the copied runtime world; do not start the old and new servers together. The installer does not install Java/Python, change firewall rules or bypass a panel's restrictions. See [INSTALL.md](docs/INSTALL.md) and [HOSTING.md](docs/HOSTING.md) for advanced setup and troubleshooting. macOS/iPhone has manual gameplay coverage; Linux/Windows and a real hosting provider still need community validation.

## Screenshots and scope

The [six original screenshots](docs/GALLERY.md) show unmodified iPhone Bedrock connected to our Beta test server. The TU1 tutorial map is a demonstration world, **not part of the download**. Its conversion is credited separately; download any map from its own creator/distributor.

The experimental ARM64/Metal Java-client work is a separate experiment and is **not included in BetaBridge**. Bedrock uses its normal renderer; lighting and some visuals will differ from Java Beta.

## Source, credits and licensing

Our code is licensed under **GPL-3.0-or-later**. Minecraft textures, sounds, music, sample metadata and third-party software retain their owners' rights; our code license does not relicense them. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) and [resource-pack notes](docs/RESOURCE-PACK.md).

Built on [Geyser](https://github.com/GeyserMC/Geyser), [ViaProxy](https://github.com/ViaVersion/ViaProxy), [ViaVersion](https://github.com/ViaVersion/ViaVersion) and [ViaLegacy](https://github.com/ViaVersion/ViaLegacy). Built with **OpenAI Codex**; the human maintainer and community remain responsible for review, testing and releases. Not affiliated with or endorsed by Mojang, Microsoft, OpenAI or the upstream compatibility projects.

Bug reports and reproducible test cases are welcome. Start with [CONTRIBUTING.md](CONTRIBUTING.md). Do not upload your account files, tokens, private worlds or unredacted logs.
