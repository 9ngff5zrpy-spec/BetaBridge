# Release preparation validation

Release candidate: **0.3.8-alpha.1**, resource pack **0.3.7**. Prepared on 2026-10-19 in America/Santiago. Experimental; built with OpenAI Codex.

## Completed locally

- Compiled all three own-code artifacts with pinned ViaProxy/Geyser dependencies and ran the standalone identity, names, skin-cache, presentation and fence-collision checks.
- Compared 56 personal-grid recipes and 148 table recipes with the original Beta server oracle, covering mirrored/offset/invalid inputs and metadata.
- Checked jukebox start/stop packet round trips and matching audio assets, fishing-bite packet parsing/encoding, every fog identifier over day/night cycles, and 72 chest faces against the original client.
- Rebuilt the pack from the original client, indexed classic audio and pinned official metadata. The resulting archive exactly matched the distributed pack's SHA-256: `df7d0e2c75ddb3adc92e2fedfbe2e50136a8cf412a46240ff9a24e8cb1dc18b2`.
- Prepared an isolated new installation and generated a world, started the original Beta server plus ViaProxy/Geyser, and shut down with ordinary chunk saving.
- Copied that stopped world into a separate hosting-style installation using `--bind 0.0.0.0`, keeping both Java listeners on loopback. Confirmed Java TCP listeners, Bedrock RakNet UDP pong, signaling HTTP, console commands and clean shutdown/save.
- Confirmed SIGTERM shutdown saves normally, as required for hosting panels; checked the same startup/network/save flow from an extracted community-preview ZIP.
- Confirmed setup refuses an existing installation without changing its world file.
- Tested automatic installation from an enclosing ZIP folder: JAR/world detection, original-file preservation, existing configuration/operator copying, repeat-install and missing-world refusal, generated launchers, Bedrock responses and SIGTERM saving through the generated launcher.
- Packaged public inputs through an explicit allowlist. Runtime directories, credentials, world saves, original Minecraft JARs and upstream dependency binaries are excluded.

These checks ran on macOS. They do not certify a Linux provider, firewall/NAT, public internet gameplay, every future Bedrock update or all inventory/vehicle edge cases. The gameplay matrix records prior human testing on the iPhone. New runtime scripts are separate from the original running lab.
