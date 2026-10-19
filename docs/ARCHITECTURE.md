# Architecture

BetaBridge is an experimental GPL Java plugin/extension/agent collection, developed with OpenAI Codex. It reuses the general protocol translators supplied by the upstream projects.

```text
Unmodified Bedrock + automatically offered resource pack
                    │
                  Geyser
                    │
   BetaHud extension: crafting, interactions, visuals, HUD
                    │
      ViaProxy + ViaVersion + ViaLegacy
         BetaBridge plugin: identity, real bite/audio events
                    │ private loopback TCP
        Original Java Beta 1.6.6 server
        + Beta166ServerCompat Java agent
```

`BetaBridge.jar` is a ViaProxy plugin. It associates the verified Bedrock/Xbox session with the correct backend connection, maintains legacy names/player files, prepares supported skin-cache data and forwards server-confirmed presentation events.

`BetaHud.jar` is a Geyser extension, deliberately pinned to build 1262. It supplies Beta crafting-result prediction and container adapters, cursor-based drop requests, narrow interaction translations, custom fence/chest/jukebox visuals, the classic boat actor and server-owned rider controls, sleep/mount confirmations, jukebox voices, HUD hiding and fog selection. Geyser's normal container validation remains in use; the original server validates actual actions and quantities.

`Beta166ServerCompat.jar` is loaded using `-javaagent`. It verifies the original server hash and expected bytecode sites before adapting classes in memory. It respects configured spawn protection, makes the original server confirm the held stack after use, and emits a reserved original-protocol level event at the real fishing bite. The plugin consumes that event only for an authenticated Bedrock session and verifies its live owned hook. Catch timing, random eligibility and fish creation remain original.

The pack provides classic textures, translated custom-block visuals, classic sound routes, C418-only music routes, fog definitions and the compact HUD. It does not turn Bedrock's renderer into the Java Beta renderer.

The server and proxy run on the same host. The original Java backend is offline-mode on loopback; Geyser still validates Xbox authentication for Bedrock. `checkEnvironment` rejects unsupported endpoint/authentication arrangements. This is not a generic remote-server joining client or an authentication bypass.

There is no bundled modified Minecraft server, Bukkit server, Metal renderer, tutorial world, player-account database or private test runtime. Earlier 1.4 compatibility branches remain in the source but are not advertised as supported by this release's installer.
