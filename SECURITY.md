# Security and privacy

This is an experimental community preview, not a hardened public-server product. Java Beta is old software. Hosting uses a co-located gateway and server; this is not a security certification of Beta, Geyser or ViaProxy. Public internet gameplay has not been validated by this release's local smoke tests.

Keep both offline Java TCP listeners on `127.0.0.1`. Do not expose them or disable Geyser's Xbox validation. The plugin rejects unsupported internal endpoint, forwarding and authentication arrangements. Bedrock alone can bind publicly with `--bind 0.0.0.0`; the installer does not open firewall/router ports. Run under an unprivileged account and configure host/provider access rules before publishing an address. See [hosting instructions](docs/HOSTING.md).

Runtime player names, XUID mappings, skin caches, account files and logs can contain personal information. Never commit them. Only share minimal sanitized logs with a bug report. No credentials or player registry is supplied in the release.

For a suspected vulnerability, do not post credentials or an exploit against a live server. Use GitHub's private vulnerability reporting feature if enabled by the maintainer, or ask for a private contact without disclosing the exploit publicly. A private-reporting channel must be configured before claiming it is available.
