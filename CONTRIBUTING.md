# Contributing

BetaBridge is a free, experimental project built with OpenAI Codex. Contributions should be reviewable and preserve original-server authority. Please disclose substantial AI assistance and verify generated code before submitting it.

1. Read the compatibility matrix and architecture notes.
2. Use the pinned dependency builds; do not silently update Geyser internals.
3. Build with `python3 tools/build.py --download --test`.
4. For recipe/fishing/chest oracles, supply your own original Minecraft JARs to `tools/verify.py`.
5. Test gameplay in a disposable world and describe the actual devices/versions used.

Do not include Minecraft JARs, decompiled game sources, Microsoft/Xbox tokens, real player identity registries, skins from other users, private worlds or unredacted logs in a pull request. Keep source changes separate from locally generated resource files and runtime data.

A passing packet roundtrip is not proof that a Bedrock client accepts a particle, UI or entity model. Mark manual verification separately from automated tests. Bug reports should contain reproduction steps, expected behavior, actual behavior, the Bedrock version and whether Java Beta behaves correctly at the same time.

Prefer existing Geyser/ViaLegacy facilities when they solve the problem. Avoid a second simulation of server-owned physics, guessed inventory grants or timers that invent fishing bites. Propose architectural changes before replacing a working compatibility route.
