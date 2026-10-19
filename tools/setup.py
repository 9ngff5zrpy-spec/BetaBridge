"""Prepare a NEW co-located Beta 1.6.6 installation. Existing destinations are refused."""
import argparse
import ipaddress
import json
from pathlib import Path
import shutil
import zipfile
from common import ROOT, LOCK, dependencies, verify, digest

def port(value):
    number = int(value)
    if not 1 <= number <= 65535:
        raise argparse.ArgumentTypeError('Port must be 1..65535')
    return number

def prepare(args):
    target = args.runtime.resolve()
    if target.exists():
        raise ValueError('Destination already exists; no files were changed: ' + str(target))
    server = verify(args.server, LOCK['minecraftServer']['sha256'])
    pack = verify(args.pack or ROOT / LOCK['pack']['path'], LOCK['pack']['sha256'])
    with zipfile.ZipFile(pack) as archive:
        if archive.testzip() is not None:
            raise ValueError('Invalid resource pack archive')
    address = ipaddress.ip_address(args.bind)
    if address.version != 4 or address.is_multicast:
        raise ValueError('Use an IPv4 listener address, such as 0.0.0.0 or an IP assigned to this host.')
    if len({args.java_port, args.proxy_port, args.bedrock_port, args.nethernet_port}) != 4:
        raise ValueError('Choose four distinct ports.')
    for name in ('BetaBridge.jar', 'BetaHud.jar', 'Beta166ServerCompat.jar'):
        if not (ROOT / 'build' / name).is_file():
            raise ValueError('Build first: python tools/build.py --download --test')
    deps = dependencies(args.deps_dir, args.download)
    world = args.world.resolve() if args.world else None
    if world and (not world.is_dir() or not (world / 'level.dat').is_file()):
        raise ValueError('--world must be a stopped Beta 1.6.6 world directory containing level.dat.')
    # Validate inputs before creating anything. The only destination is a new directory.
    for folder in ('lib', 'beta', 'proxy/plugins/Geyser/extensions', 'proxy/plugins/Geyser/packs', 'logs'):
        (target / folder).mkdir(parents=True, exist_ok=True)
    shutil.copyfile(server, target / 'lib/minecraft-server-b1.6.6.jar')
    shutil.copyfile(deps['ViaProxy.jar'], target / 'lib/ViaProxy.jar')
    shutil.copyfile(deps['Geyser-ViaProxy.jar'], target / 'proxy/plugins/Geyser-ViaProxy.jar')
    for name, destination in [('BetaBridge.jar', 'proxy/plugins/BetaBridge.jar'),
                              ('BetaHud.jar', 'proxy/plugins/Geyser/extensions/BetaHud.jar'),
                              ('Beta166ServerCompat.jar', 'lib/Beta166ServerCompat.jar')]:
        shutil.copyfile(ROOT / 'build' / name, target / destination)
    shutil.copyfile(pack, target / 'proxy/plugins/Geyser/packs/BetaBridge-b1.6.6.mcpack')
    if world:
        # Copy only a user-selected stopped world; never move or upgrade the original.
        shutil.copytree(world, target / 'beta/world', symlinks=False)
    (target / 'beta/server.properties').write_text(
        'server-ip=127.0.0.1\nserver-port=' + str(args.java_port) + '\nonline-mode=false\n'
        'level-name=world\nmax-players=8\nspawn-protection=0\nallow-flight=true\n'
        'allow-nether=true\nspawn-monsters=true\nspawn-animals=true\npvp=true\nview-distance=8\n')
    (target / 'proxy/viaproxy.yml').write_text(
        'bind-address: 127.0.0.1:' + str(args.proxy_port) + '\n'
        'target-address: 127.0.0.1:' + str(args.java_port) + '\n'
        'target-version: b1.6-b1.6.6\nproxy-online-mode: false\nauth-method: NONE\n'
        'wildcard-domain-handling: NONE\nlog-ips: false\nallow-beta-pinging: false\n')
    (target / 'proxy/vialegacy.yml').write_text(
        'enable-b1_7_3-sprinting: false\nold-biomes: true\nsound-emulation: true\n')
    (target / 'proxy/plugins/Geyser/config.yml').write_text(
        'bedrock:\n  address: ' + str(address) + '\n  port: ' + str(args.bedrock_port) + '\n'
        '  webrtc-port: ' + str(args.nethernet_port) + '\n  transport: both\n  clone-remote-port: false\n'
        'java:\n  auth-type: offline\n'
        'motd:\n  primary-motd: BetaBridge - Beta 1.6.6\n  secondary-motd: Community preview\n'
        '  passthrough-motd: false\n  integrated-ping-passthrough: false\n'
        'gameplay:\n  server-name: BetaBridge\n  force-resource-packs: true\n'
        'advanced:\n  bedrock:\n    validate-bedrock-login: true\n'
        '    use-waterdogpe-forwarding: false\n    use-haproxy-protocol: false\n'
        'enable-metrics: false\n')
    config = target / 'proxy/plugins/BetaBridge/beta-bridge.properties'
    config.parent.mkdir(exist_ok=True)
    config.write_text('proxy-port=' + str(args.proxy_port) + '\nbackend-port=' + str(args.java_port) + '\n'
                      'beta-player-directory=../beta/world/players\nskin-cache-directory=../skin-cache\n')
    state = {'version': LOCK['version'], 'javaPort': args.java_port, 'proxyPort': args.proxy_port,
             'bind': str(address), 'bedrockPort': args.bedrock_port, 'nethernetPort': args.nethernet_port,
             'serverSha256': digest(target / 'lib/minecraft-server-b1.6.6.jar'), 'boatRenderer': 'custom'}
    (target / 'installation.json').write_text(json.dumps(state, indent=2) + '\n')
    print('Prepared NEW installation:', target)
    print('Start: python tools/run.py --runtime "' + str(target) + '"')

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--runtime', type=Path, default=ROOT / 'runtime')
    parser.add_argument('--server', type=Path, required=True, help='Your unmodified original Beta 1.6.6 server JAR.')
    parser.add_argument('--pack', type=Path)
    parser.add_argument('--world', type=Path, help='Optional stopped Beta 1.6.6 world to COPY.')
    parser.add_argument('--bind', default='127.0.0.1', help='Bedrock listener: 0.0.0.0 for hosting, assigned LAN IP for LAN; loopback by default.')
    parser.add_argument('--java-port', type=port, default=25565)
    parser.add_argument('--proxy-port', type=port, default=25568)
    parser.add_argument('--bedrock-port', type=port, default=19132)
    parser.add_argument('--nethernet-port', type=port, default=19133)
    parser.add_argument('--deps-dir', type=Path)
    parser.add_argument('--download', action='store_true')
    try:
        prepare(parser.parse_args())
    except (ValueError, OSError) as error:
        parser.exit(1, str(error) + '\n')
