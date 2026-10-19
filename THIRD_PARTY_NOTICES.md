# Third-party notices and credits

BetaBridge was built with **OpenAI Codex**, with human-directed implementation, testing and review. AI assistance is disclosed; it does not imply endorsement, a security audit or correctness guarantees.

Our code is GPL-3.0-or-later. The following projects and content retain their own licenses and rights:

- **Geyser**, GeyserMC: https://github.com/GeyserMC/Geyser — Bedrock/Java bridge. The pinned ViaProxy plugin is downloaded separately from the official project; it is not included in our release ZIP.
- **ViaProxy**, ViaVersion team: https://github.com/ViaVersion/ViaProxy — standalone Java protocol gateway, downloaded separately from its official release.
- **ViaVersion** and **ViaLegacy**, ViaVersion team and contributors: https://github.com/ViaVersion/ViaVersion and https://github.com/ViaVersion/ViaLegacy — upstream protocol translation, included in the separately obtained ViaProxy distribution under upstream terms. BetaBridge does not claim authorship of their translators.
- **ASM**, INRIA/France Telecom and contributors: https://asm.ow2.io/ — the server agent includes ASM classes from the pinned dependency. The original notice is included in `licenses/ASM-LICENSE.txt` and inside the agent JAR. This notice does not change ASM's license.
- **Minecraft and Bedrock samples**, Mojang/Microsoft: https://github.com/Mojang/bedrock-samples — textures, sounds, UI/sample references and game content retain their owners' rights. Sample references are fetched at a pinned commit when rebuilding the pack.
- **C418**, Daniel Rosenfeld: https://c418.org/ — original Minecraft music. No ownership, authorship or GPL licensing of those recordings is claimed.
- **LegacyFix**: optional Java-client-side skin interoperability; not bundled, required by neither the Beta server nor Bedrock players.
- **Xbox TU1 tutorial world conversion**, shown only in screenshots: https://theminecraftarchitect.com/tutorial-worlds/tu1 — the tutorial world is not included. The screenshots are demonstrations, not a map license grant.

No Minecraft server/client JARs, Microsoft/Xbox tokens, private player data, tutorial map download or upstream dependency binaries are included in the public source or community-preview ZIP. Users supply their own original Beta server JAR and obtain dependencies from upstream.

Not an official Minecraft product. Not approved by or associated with Mojang or Microsoft. No endorsement by OpenAI or the upstream compatibility projects is implied.
