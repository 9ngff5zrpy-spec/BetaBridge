"""Run the prepared local gateway; console input goes to Beta. Type stop or press Ctrl+C."""
import argparse
import json
from pathlib import Path
import queue
import signal
import socket
import subprocess
import sys
import threading
import time
from common import ROOT, LOCK, java_tool, verify

def run(runtime):
    runtime = runtime.resolve()
    state = json.loads((runtime / 'installation.json').read_text())
    verify(runtime / 'lib/minecraft-server-b1.6.6.jar', LOCK['minecraftServer']['sha256'])
    # Fail before launching either process if a required listener is occupied.
    for host, port, kind in [('127.0.0.1', state['javaPort'], socket.SOCK_STREAM),
                             ('127.0.0.1', state['proxyPort'], socket.SOCK_STREAM),
                             (state['bind'], state['bedrockPort'], socket.SOCK_DGRAM),
                             (state['bind'], state['bedrockPort'], socket.SOCK_STREAM),
                             (state['bind'], state['nethernetPort'], socket.SOCK_DGRAM)]:
        with socket.socket(socket.AF_INET, kind) as probe:
            if kind == socket.SOCK_STREAM:
                probe.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            probe.bind((host, port))
    commands = queue.Queue()
    def read_input():
        for line in sys.stdin:
            commands.put(line.strip())
    threading.Thread(target=read_input, daemon=True).start()
    children = []
    logs = []
    def start(command, cwd, name, marker):
        path = runtime / 'logs' / name
        output = path.open('w', encoding='utf-8')
        logs.append(output)
        process = subprocess.Popen(command, cwd=cwd, stdin=subprocess.PIPE,
                                   stdout=output, stderr=subprocess.STDOUT, text=True)
        children.append(process)
        deadline = time.monotonic() + 90
        while time.monotonic() < deadline:
            if process.poll() is not None:
                raise RuntimeError('Process exited: ' + str(path))
            if marker in path.read_text(errors='replace'):
                return process
            time.sleep(.2)
        raise RuntimeError('Startup timeout: ' + str(path))
    java = java_tool('java')
    def terminate(signum, frame):
        raise KeyboardInterrupt
    # Hosting panels normally stop foreground processes with SIGTERM.
    signal.signal(signal.SIGTERM, terminate)
    try:
        beta = start([java, '-Xms256M', '-Xmx1G', '-javaagent:' + str(runtime / 'lib/Beta166ServerCompat.jar'),
                      '-jar', str(runtime / 'lib/minecraft-server-b1.6.6.jar'), 'nogui'],
                     runtime / 'beta', 'beta.log', 'Done (')
        proxy = start([java, '-Xms256M', '-Xmx1G', '-Djava.awt.headless=true', '-DskipUpdateCheck',
                       '-Dbetabridge.profile=b1.6.6', '-Dbetabridge.boatRenderer=custom',
                       '-jar', str(runtime / 'lib/ViaProxy.jar'), 'config', 'viaproxy.yml'],
                      runtime / 'proxy', 'bridge.log', 'Started Geyser on')
        print('Ready. Java Beta: 127.0.0.1:' + str(state['javaPort']), flush=True)
        print('Bedrock: ' + state['bind'] + ':' + str(state['bedrockPort']), flush=True)
        print('Type original Beta commands here (time set 0, list, stop). Ctrl+C also saves and stops.', flush=True)
        while beta.poll() is None and proxy.poll() is None:
            try:
                line = commands.get(timeout=.5)
            except queue.Empty:
                continue
            if line == 'stop':
                break
            if line:
                beta.stdin.write(line + '\n')
                beta.stdin.flush()
        else:
            raise RuntimeError('A child process exited. See runtime/logs.')
    except KeyboardInterrupt:
        pass
    finally:
        # Close the gateway first; request the original server's ordinary save.
        if len(children) > 1 and children[1].poll() is None:
            children[1].terminate()
            try:
                children[1].wait(timeout=15)
            except subprocess.TimeoutExpired:
                children[1].kill(); children[1].wait()
        if children and children[0].poll() is None:
            try:
                children[0].stdin.write('stop\n'); children[0].stdin.flush()
                children[0].wait(timeout=30)
            except (BrokenPipeError, subprocess.TimeoutExpired):
                children[0].terminate(); children[0].wait(timeout=10)
        for output in logs:
            output.close()
        print('Stopped. Check runtime/logs/beta.log for the original server save status.', flush=True)

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--runtime', type=Path, default=ROOT / 'runtime')
    try:
        run(parser.parse_args().runtime)
    except (OSError, ValueError, RuntimeError) as error:
        parser.exit(1, str(error) + '\n')
