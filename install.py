#!/usr/bin/env python3
"""Install BetaBridge from the server folder: python3 install.py."""
import argparse
import json
import os
from pathlib import Path
import re
import socket
import subprocess
import sys

ROOT = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT / 'tools'))
from common import LOCK, digest, java_tool
from setup import prepare, port


def properties(path):
    result = {}
    if path.exists():
        for line in path.read_text(encoding='latin-1').splitlines():
            line = line.strip()
            if not line or line.startswith(('#', '!')):
                continue
            match = re.match(r'([^=:\s]+)\s*[=:]\s*(.*)', line)
            if match:
                result[match[1]] = match[2]
    return result


def install(args):
    folder = (args.server_dir or Path.cwd()).resolve()
    # ZIP tools often create one enclosing folder. Also recognize a server
    # immediately above it, so users do not have to rearrange extracted files.
    if not args.server_dir and not args.server and not any(
            digest(p) == LOCK['minecraftServer']['sha256'] for p in folder.glob('*.jar') if p.is_file()):
        parent_matches = [p for p in folder.parent.glob('*.jar')
                          if p.is_file() and digest(p) == LOCK['minecraftServer']['sha256']]
        if len(parent_matches) == 1:
            folder = folder.parent
    if not folder.is_dir():
        raise ValueError('Server folder does not exist: ' + str(folder))
    runtime = folder / 'betabridge-runtime'
    launchers = [folder / ('Start-BetaBridge' + suffix) for suffix in ('.py', '.sh', '.command', '.bat')]
    if runtime.exists() or any(path.exists() for path in launchers):
        raise ValueError('BetaBridge is already installed or a previous installation is present. '
                         'Use Start-BetaBridge.py; this installer will not overwrite your world.')
    version = subprocess.run([java_tool('java'), '-version'], capture_output=True, text=True, check=True)
    match = re.search(r'version "(\d+)', version.stdout + version.stderr)
    if not match or int(match[1]) < 21:
        raise ValueError('Install JDK 21, then run Install-BetaBridge again. Your current Java is too old.')
    if args.server:
        jar = args.server.resolve()
        if not jar.is_file() or digest(jar) != LOCK['minecraftServer']['sha256']:
            raise ValueError('Selected JAR is not the supported original Java Beta 1.6.6 server.')
    else:
        matches = [path for path in sorted(folder.glob('*.jar'))
                   if path.is_file() and digest(path) == LOCK['minecraftServer']['sha256']]
        if len(matches) != 1:
            raise ValueError('Put ONE original Java Beta 1.6.6 server JAR in this folder, then try again. '
                             'For an explicit file, use --server "path/to/server.jar".')
        jar = matches[0]
    original_config = folder / 'server.properties'
    settings = properties(original_config)
    name = settings.get('level-name', 'world')
    if not args.world and ('\\' in name or '\n' in name or '\r' in name):
        raise ValueError('Unusual escaped world name: use --world "path/to/world" explicitly.')
    world = args.world.resolve() if args.world else (folder / name).resolve()
    if not args.world and not world.is_relative_to(folder):
        raise ValueError('World is outside the server folder. Select it explicitly with --world.')
    if world.exists() and not (world / 'level.dat').is_file():
        raise ValueError('Detected world has no level.dat: ' + str(world))
    # If an existing server has a configured world, a missing save is an error,
    # not permission to silently generate a replacement world.
    if not world.exists() and (args.world or original_config.exists()):
        raise ValueError('Configured world was not found: ' + str(world) + '. Stop and check the server folder.')
    java_port = args.java_port or port(settings.get('server-port', '25565'))
    hosts = {'127.0.0.1'}
    configured_ip = settings.get('server-ip', '')
    if configured_ip and re.fullmatch(r'\d+\.\d+\.\d+\.\d+', configured_ip) and configured_ip != '0.0.0.0':
        hosts.add(configured_ip)
    for host in hosts:
        for check_port in {java_port, port(settings.get('server-port', '25565'))}:
            with socket.socket() as probe:
                probe.settimeout(.3)
                if probe.connect_ex((host, check_port)) == 0:
                    raise ValueError('The server port is in use. STOP the existing server before installing.')
    print('\nBetaBridge ' + LOCK['version'] + ' — experimental, built with OpenAI Codex', flush=True)
    print('Server found: ' + jar.name, flush=True)
    print('World: ' + (str(world) + ' (will be COPIED)' if world.exists() else 'new world'), flush=True)
    print('Original files stay intact. The installed world will be betabridge-runtime/beta/world.', flush=True)
    if not all((ROOT / 'build' / name).is_file() for name in
               ('BetaBridge.jar', 'BetaHud.jar', 'Beta166ServerCompat.jar')):
        print('Building BetaBridge from source; this needs JDK 21...', flush=True)
        from build import build
        build(args.deps_dir, True, True)
    print('Checking official dependencies and preparing the server...', flush=True)
    import tempfile
    final_runtime = runtime
    with tempfile.TemporaryDirectory(prefix='.betabridge-install-', dir=folder) as staging:
        runtime = Path(staging) / 'runtime'
        import contextlib, io
        with contextlib.redirect_stdout(io.StringIO()):
            prepare(argparse.Namespace(runtime=runtime, server=jar, pack=None,
                world=world if world.exists() else None, bind=args.bind, java_port=java_port,
                proxy_port=args.proxy_port, bedrock_port=args.bedrock_port,
                nethernet_port=args.nethernet_port, deps_dir=args.deps_dir, download=True))
        if original_config.exists():
            # Preserve normal existing settings. Security, world location and transport
            # overrides are appended last because Java Properties uses the last value.
            content = original_config.read_text(encoding='latin-1')
            content += ('\n# BetaBridge internal transport and copied world\n'
                        'server-ip=127.0.0.1\nserver-port=' + str(java_port) + '\n'
                        'online-mode=false\nlevel-name=world\nallow-flight=true\n')
            (runtime / 'beta/server.properties').write_text(content, encoding='latin-1')
        for name in ('ops.txt', 'banned-players.txt', 'banned-ips.txt', 'white-list.txt'):
            path = folder / name
            if path.is_file():
                import shutil
                shutil.copyfile(path, runtime / 'beta' / name)
        runtime.rename(final_runtime)
    runtime = final_runtime
    relative = os.path.relpath(ROOT / 'tools/run.py', folder)
    launchers[0].write_text('#!/usr/bin/env python3\nimport os, pathlib, sys\n'
        'folder = pathlib.Path(__file__).resolve().parent\n'
        'os.chdir(folder)\n'
        'os.execv(sys.executable, [sys.executable, str(folder / ' + repr(relative) + '), '
        '"--runtime", str(folder / "betabridge-runtime")])\n')
    shell = '#!/bin/sh\ncd -- "$(dirname -- "$0")" || exit 1\nexec python3 Start-BetaBridge.py "$@"\n'
    for path in launchers[1:3]:
        path.write_text(shell); path.chmod(0o755)
    launchers[3].write_text('@echo off\r\ncd /d "%~dp0"\r\npy -3 Start-BetaBridge.py\r\nif errorlevel 1 pause\r\n')
    launchers[0].chmod(0o755)
    print('\nINSTALLED. Start with: python3 Start-BetaBridge.py', flush=True)
    print('macOS: Start-BetaBridge.command | Windows: Start-BetaBridge.bat', flush=True)
    print('Hosting panel startup: python3 Start-BetaBridge.py', flush=True)
    print('Bedrock players: YOUR_PUBLIC_IP:' + str(args.bedrock_port) + '; accept the automatic pack.', flush=True)
    print('Host allocations: TCP+UDP ' + str(args.bedrock_port) + ', UDP ' + str(args.nethernet_port), flush=True)
    print('Use the new launcher from now on. Do not start the old server at the same time.', flush=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--server-dir', type=Path)
    parser.add_argument('--server', type=Path)
    parser.add_argument('--world', type=Path)
    parser.add_argument('--bind', default='0.0.0.0')
    parser.add_argument('--java-port', type=port)
    parser.add_argument('--proxy-port', type=port, default=25568)
    parser.add_argument('--bedrock-port', type=port, default=19132)
    parser.add_argument('--nethernet-port', type=port, default=19133)
    parser.add_argument('--deps-dir', type=Path)
    try:
        install(parser.parse_args())
    except (OSError, ValueError, RuntimeError, subprocess.CalledProcessError) as error:
        print('\nInstallation stopped: ' + str(error), file=sys.stderr)
        print('Your original server and world were not replaced.', file=sys.stderr)
        sys.exit(1)
