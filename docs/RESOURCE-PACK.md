# Classic resource pack

Bedrock players accept the pack automatically when joining. No client modification, custom launcher, behavior-pack installation or separate phone-side installer is required.

The community-preview download includes the tested pack, version 0.3.7. The code release version is independent. The supported pack's SHA-256 is pinned in `dependencies.lock.json`; setup verifies it before copying it into Geyser's pack directory.

The pack contains classic Minecraft textures/sounds and C418 music, plus BetaBridge's mappings, generated geometry, visual adaptations and HUD changes. Minecraft/C418 content retains its original ownership and is **not licensed under the project's GPL**. Do not describe the complete pack as newly composed music, original texture artwork or content owned by BetaBridge. The pack is unofficial and carries no Mojang endorsement. See [Minecraft's EULA](https://www.minecraft.net/en-us/eula) and [Usage Guidelines](https://www.minecraft.net/en-us/usage-guidelines) for the applicable terms; this project's license grants rights only to our own work.

## Rebuild locally

Developers need Pillow, their own original Beta 1.6.6 client JAR and the classic resource files. Audio inputs are verified against `data/legacy-audio-index.json`; directory paths such as `newsound/random/splash.ogg` and standard Java `assets/objects/<prefix>/<hash>` layouts are accepted. Do not substitute unrelated modern sounds with the same filename.

```sh
python3 -m pip install Pillow
python3 tools/fetch_references.py
python3 tools/build_resourcepack.py \
  --client "/path/to/original-beta-1.6.6-client.jar" \
  --resources "/path/to/classic/resources"
```

The builder downloads no Minecraft JAR or music. `fetch_references.py` retrieves official Mojang Bedrock sample metadata at a pinned commit and verifies hashes. Its cache and all locally generated inputs/outputs are excluded from Git. The generator refuses an existing output directory; choose a new `--output-dir` to rebuild without deleting anything.

The default builder reproduces the distributed pack, including both the native-boat experiment and the fallback custom hull assets. The release runner selects the **confirmed custom Beta hull**. The builder's `--boat-renderer custom` produces a reduced alternative, not the exact pinned release archive. Do not switch rendering paths when reporting the supported release's behavior.

Other resource packs that override `ui/hud_screen.json`, custom block textures, music or fog can conflict. Regional biome colors are approximate equivalents; lighting remains Bedrock's.
