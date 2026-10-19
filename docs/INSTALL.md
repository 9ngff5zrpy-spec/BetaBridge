# Installation and developer builds

BetaBridge is experimental and was built with OpenAI Codex. The release target is the original **Java Beta 1.6.6**, with Geyser 2.12.0 build 1262 and ViaProxy 3.4.14. The tested Bedrock build is **26.52 / protocol 2193**; future Bedrock updates are not automatically supported.

## Requirements

- JDK 21 (including `java` and, for source builds, `javac`). This requirement comes from the modern bridge stack, not original Minecraft Beta. The installer uses Java 21 for both processes. Set `JAVA_HOME` or put its executables on PATH.
- Python 3.9+; no third-party Python modules are needed for installation.
- Your own unmodified Beta 1.6.6 server JAR, SHA-256:

  ```text
  af36c9c708ec9b322378acb319b0da8a1d76ac2185c595a077d0c069835b8916
  ```

- The resource pack supplied with the community-preview release. The client accepts it automatically on joining; **Bedrock itself is unmodified**.
- A VPS, dedicated server or hosting container allowing Python, JDK 21 and both Java processes in the same network namespace. Both internal Java TCP listeners stay bound to `127.0.0.1`. See [HOSTING.md](HOSTING.md) for provider requirements and public ports.

## Automatic install (recommended)

Stop your original Beta server. Extract the community-preview ZIP into its server folder, alongside the original Beta 1.6.6 JAR and `server.properties`. If extraction creates one enclosing BetaBridge folder, the installer also detects the server immediately above it.

- **macOS:** open `Install-BetaBridge.command`.
- **Windows:** open `Install-BetaBridge.bat` (Python launcher `py` required).
- **Linux / hosting terminal:** run `python3 install.py` from that folder.

The installer detects the supported JAR by SHA-256 and the world from `level-name` in `server.properties`, checks Java, fetches verified official dependencies, copies the world, preserves normal settings/operator lists and generates Start-BetaBridge launchers. It creates `betabridge-runtime` without replacing the original files. It refuses an existing installation and stops if the configured world is missing or the server port is in use. Stop the server yourself first; port detection is a guard, not proof that an unresponsive process has stopped.

Start using `Start-BetaBridge.command` (Mac), `Start-BetaBridge.bat` (Windows), or `python3 Start-BetaBridge.py` (terminal/panel). Keep the extracted tools in place. The launcher uses them via a relative path, so moving the complete server folder together works.

From now on, the active world is **betabridge-runtime/beta/world**. New gameplay changes do not appear in the original world's rollback copy. Do not start the old JAR separately at the same time.

The default Bedrock bind is `0.0.0.0`; players connect to your public IP/domain, never that wildcard address. For a LAN test use the server's LAN address. With different hosting allocations, install using:

```sh
python3 install.py --bedrock-port 20000 --nethernet-port 20001
```

Use your provider's actual ports. Optional explicit selection: `--server-dir /path/to/server`, `--server /path/to/original.jar`, `--world /path/to/stopped/world`, `--java-port`, `--proxy-port`, `--bind`, and `--deps-dir` for cached verified dependencies. A fresh folder with one original JAR and no server.properties/world is allowed and creates a new world on first start. The installer requires a standard unmodified Beta server, not arbitrary modded JARs.

Java/Python and hosting allocations still need to exist; the installer does not install system packages or open firewalls. Public hosts must support both Java processes in the same network namespace. See [HOSTING.md](HOSTING.md).

On first startup, Geyser may separately download a modern Minecraft JAR to extract mapping resources. This is upstream runtime behavior, not a supplied Beta server or a file to redistribute with BetaBridge. The host needs outbound HTTPS and writable runtime storage.

## Advanced manual setup

The original explicit new-runtime workflow remains available:

```sh
python3 tools/setup.py --download --server "/path/to/original-beta-server.jar" --world "/path/to/stopped/world" --bind 0.0.0.0
python3 tools/run.py
```

This advanced workflow defaults to `runtime` and creates fresh server settings. It does not automatically copy operator lists or migrate all existing configuration. The automatic installer above defaults to `betabridge-runtime` and preserves normal settings. In both cases the Java transport is loopback/offline, and allow-flight is enabled to avoid Beta's old floating kick; Xbox authentication remains enabled at the Bedrock gateway.

## Source checkout

The source archive contains no compiled runtime or Minecraft game files. Obtain the resource pack from the matching release and place it at `resourcepack/BetaBridge-b1.6.6.mcpack`, then:

```sh
python3 tools/build.py --download --test
python3 tools/setup.py --download --server "/path/to/original-beta-server.jar" --bind 0.0.0.0
python3 tools/run.py
```

Outputs are `build/BetaBridge.jar`, `build/BetaHud.jar` and `build/Beta166ServerCompat.jar`. The build never deploys into a running installation. Official dependency URLs and hashes are in `dependencies.lock.json`. Use `--deps-dir /path/to/pinned-jars` to build or install offline from already downloaded dependencies. A matching release includes the exact corresponding source.

## Ports and connecting

| Service | Default | Exposure |
|---|---:|---|
| Original Java Beta server | TCP 25565 | Loopback only |
| Internal modern Java gateway | TCP 25568 | Loopback only |
| Bedrock RakNet and built-in signaling | UDP + TCP 19132 | Public hosting allocation / selected interface |
| Bedrock NetherNet | UDP 19133 | Public hosting allocation / selected interface |

The installer accepts `--java-port`, `--proxy-port`, `--bedrock-port`, `--nethernet-port` and `--runtime` for a separate new installation. Use four distinct ports. Allocate the Bedrock ports in the hosting panel and allow them in the host/cloud firewall; home hosting also needs router forwarding. Rules are not changed automatically. Do not expose the offline Java backend or disable `validate-bedrock-login`.

Original Java Beta clients on the server machine can join `127.0.0.1:25565`. Remote Java-player access is outside this preview's default configuration.

## Stop, logs and troubleshooting

Keep the runner terminal open. Type original Beta commands directly; `stop` or Ctrl+C shuts down the gateway first and requests an ordinary server save. For automatic installations, logs are in `betabridge-runtime/logs/beta.log` and `betabridge-runtime/logs/bridge.log` (advanced manual installs use `runtime/logs`).

- **Hash mismatch:** use the exact supported unmodified server JAR or dependency; do not bypass the check.
- **Cannot bind:** another server is using a port, or the selected IP is not assigned to this machine.
- **Phone cannot see the server:** check the selected LAN IP, local firewall, same network, and the tested Bedrock build.
- **Missing textures/HUD:** accept the pack; disable other resource packs that override the same HUD or block definitions.
- **New Bedrock release rejected:** Geyser is deliberately pinned because the adapters use its internals. An arbitrary Geyser update is not a supported fix.
- **Names/skins:** standard skins are limited; Persona/custom geometries may fall back. Xbox account linking to a paid Java identity is not implemented.

## Optional verification

After `tools/build.py --test`, run the pack contracts and original-game oracle tests:

```sh
python3 tools/verify.py --server "/path/to/original-beta-server.jar" --client "/path/to/original-beta-client.jar"
```

The client JAR is optional and used only by the chest texture oracle, not by Bedrock users. These tests are not an automated iPhone gameplay test. See [COMPATIBILITY.md](COMPATIBILITY.md) for manual test coverage.
