# Release review

This is an experimental release built with OpenAI Codex. A green source build is not proof that every Bedrock mechanic works.

- Build the three own-code JARs with the pinned official dependencies: `python3 tools/build.py --download --test`.
- Run `tools/verify.py` with your original Minecraft JARs to check the recipe oracle, fishing event encoding, jukebox channels, fog identifiers and chest faces.
- Rebuild the resource pack using the documented original inputs and official metadata; compare its SHA-256 with the lock file.
- Prepare a fresh installation in an unused directory and isolated ports. Confirm startup, Bedrock ping/signaling, ordinary shutdown and world persistence.
- Test authenticated Bedrock gameplay on the exact version advertised. Recheck boats, fishing, containers, crafting, inventory drops, sleep, fences, minecart controls, powered rails and jukebox playback/stop across Java and Bedrock.
- Test the actual provider/network before claiming public hosting validation. Local socket tests do not cover firewall/NAT or console platform restrictions.
- Build the source/community archives with `python3 tools/package_release.py`. Inspect the entries and checksums. No Minecraft JAR, world, player registry, account file or upstream dependency binary belongs in them.
- Upload the pack as a separate release asset too; the source archive excludes it. Preserve notices and corresponding source with binary releases.
- Keep the six original screenshots and mark the tutorial world as a demonstration not included in the download.
- Review English announcement text, actual repository/release URLs and community rules before posting. Do not describe unverified behavior as fixed or attribute every compatibility issue to ViaLegacy.
