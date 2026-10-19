# BetaBridge 0.3.8-alpha.1 — community preview

**Experimental. Built with OpenAI Codex. Free to try and contribute to.**

This preview packages the locally tested Java Beta 1.6.6 bridge, classic resource pack and a new clean-install workflow. It is not a guarantee of compatibility with all Bedrock builds or hosting environments.

The recommended install is now `python3 install.py`, with Mac/Windows installer wrappers. It detects the original JAR and world, downloads verified dependencies, preserves normal settings and generates Start-BetaBridge launchers. Original files stay intact; the active copy lives in `betabridge-runtime/beta/world`.

Highlights include original crafting/autocrafting, inventory synchronization, sleeping, classic fence collisions/corners, mounted redstone controls, Beta boat appearance and server-owned movement, real fishing-bite cues, jukebox interactions, full-block chests, biome-matched classic colors, blue water, 64-block fog, C418-only music and the compact HUD.

Bedrock players use the unmodified game and only accept the automatically offered resource pack. Server operators supply their own original Java Beta 1.6.6 JAR. Our agent adapts three behaviors in memory without overwriting that JAR.

Pinned/tested stack: ViaProxy 3.4.14; Geyser 2.12.0 build 1262; Bedrock 26.52/protocol 2193; JDK 21. Human gameplay tests were on macOS/iPhone. New installation scripts are tested separately with an isolated world; Windows/Linux and public hosting are not claimed as verified.

Known limits: standard skins only, no Beta/Xbox achievements, regional rather than exact per-column biome tint, Bedrock lighting differences, remaining exhaustive cross-device jukebox tests, and no arbitrary remote Java backend support. See COMPATIBILITY.md.

The tutorial world, Minecraft JARs, upstream dependency binaries and the separate Java Metal experiment are not included. The resource pack has separate content/attribution notes; GPL applies to our code.
