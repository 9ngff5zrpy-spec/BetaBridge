# VPS, dedicated servers and hosting panels

BetaBridge is intended to let unmodified Bedrock players reach an original Java Beta 1.6.6 world. LAN is only one testing option. A public deployment exposes the **Bedrock gateway**, while the two internal Java listeners remain private.

The host must run both Java processes and Python in the **same network namespace**, with access to the copied world's player files. The tested adapters currently require loopback between ViaProxy and Beta. Installing the gateway on a home computer and pointing it at an unrelated remote Beta host is not supported.

## Check the host first

| Hosting arrangement | Requirements |
|---|---|
| VPS or dedicated machine with shell access | JDK 21, Python 3.9+, writable storage, outbound HTTPS for dependencies, and the Bedrock port allocations below |
| Custom container / configurable panel startup | Same tools, permission to spawn both Java processes, shared filesystem and network namespace, correct TCP/UDP allocations |
| Panel that only launches one selected Minecraft JAR | Needs a provider-supported custom startup/egg/image before it can run this release |
| Two isolated panel servers / containers | Not supported by this preview's loopback checks; using their public or container IP as the backend does not work |
| Paper/Spigot plugins folder | Not an installation method: BetaBridge's plugin runs inside ViaProxy, and BetaHud runs inside Geyser |

The runner gives each Java process a 1 GiB maximum heap, plus native memory, Python and OS overhead. Allocate enough memory for both; a 1 GiB total container is insufficient. Capacity and player count require measurement on your actual world.

## Install

1. Stop the old server before copying its world. Keep the original server directory intact.
2. Upload/extract the community-preview ZIP to a new directory on the host. Supply your own supported original Beta 1.6.6 server JAR.
3. Extract the package into the stopped server folder, enter its extracted folder, and run:

   ```sh
   python3 install.py
   ```

   It detects the original Beta JAR and world, installs the bridge beside them and creates `Start-BetaBridge.py`. For provider-specific allocations, use `python3 install.py --bedrock-port 20000 --nethernet-port 20001` with your actual assigned ports. Bedrock defaults to `0.0.0.0`; if the provider requires an assigned local interface, pass `--bind LOCAL_IPV4`. A public address behind NAT may not be assigned directly to the container. Players never enter `0.0.0.0`.

4. Allow **19132 TCP + UDP** and **19133 UDP**, or your selected replacements, in the panel allocations and host/provider firewall. With the configured `transport: both`, the main port handles RakNet and signaling, and the extra UDP port handles NetherNet. Home hosting also needs matching router forwarding. These transport requirements follow the [official Geyser setup guide](https://geysermc.org/wiki/geyser/setup/).
5. Start with:

   ```sh
   python3 Start-BetaBridge.py
   ```

   In a configurable panel, use that command as the foreground startup command from the extracted project's directory. Do not start the Beta JAR separately: the runner starts Beta with its required Java agent, then starts ViaProxy/Geyser. Let the provider implement the startup/image if the panel does not expose these controls.

6. Bedrock players add your **public IP or DNS name** and allocated main Bedrock port, authenticate with Xbox and accept the automatically offered resource pack. No Bedrock mods, launcher changes or manual add-on installation are needed. Platform-specific restrictions on adding external servers still apply.
7. Type `stop` in the runner console, send Ctrl+C, or let the panel send SIGTERM for an ordinary Beta save. Allow at least 60 seconds of shutdown grace; force-killing a container bypasses normal saving.

Do **not** allocate/forward the internal Java TCP ports 25565 and 25568 to the internet. Their loopback binding and Geyser's Xbox validation must remain enabled. Remote original-Java-client access needs a separate authenticated access design and is not part of these instructions.

## World and persistent data

The automatically installed world lives in `betabridge-runtime/beta/world`. Runtime player identity mappings, skin cache, configuration and logs are private server data. Preserve the complete server folder, including tools and the runtime directory when moving the installation, and stop it first. Do not publish it with the source or screenshots. The installer refuses an existing destination instead of overwriting it; it is a new-install tool, not an in-place updater.

## Verify on the real host

Wait for the runner's ready message and inspect `betabridge-runtime/logs/bridge.log` for successful Geyser startup. Test from another network using the tested Bedrock build. A local startup check cannot prove the provider's firewall, NAT or internet transport works. Share sanitized errors and the panel's startup/network capabilities when requesting help; do not send tokens, account files or a private world.

This release has local macOS/iPhone gameplay coverage and an isolated installation smoke test. Linux hosting, provider-specific containers and public internet gameplay still need independent validation. See [the compatibility matrix](COMPATIBILITY.md).
