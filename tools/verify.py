"""Optional oracle and pack tests using user-supplied original Minecraft JARs."""
import argparse
import os
from pathlib import Path
import subprocess
import zipfile
from common import ROOT, LOCK, dependencies, java_tool, verify

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--deps-dir', type=Path)
    parser.add_argument('--server', type=Path)
    parser.add_argument('--client', type=Path)
    parser.add_argument('--pack', type=Path, default=ROOT / LOCK['pack']['path'])
    args = parser.parse_args()
    deps = dependencies(args.deps_dir)
    cp = os.pathsep.join([*map(str, deps.values()), str(ROOT / 'plugin/src/main/resources'), str(ROOT / 'extension/src/main/resources'), *map(str, [ROOT / 'build/plugin', ROOT / 'build/extension',
                                               ROOT / 'build/plugin-tests', ROOT / 'build/extension-tests'])])
    def test(name, *arguments):
        subprocess.run([java_tool('java'), '-Djava.awt.headless=true', '-Dbetabridge.profile=b1.6.6',
                        '-cp', cp, name, *map(str, arguments)], check=True)
    pack = verify(args.pack, LOCK['pack']['sha256'])
    extracted = ROOT / '.cache/pack-tests'
    with zipfile.ZipFile(pack) as archive:
        for name in archive.namelist():
            if not name.endswith('/') and (name.startswith('fogs/') or name.startswith('sounds/')):
                path = extracted / name
                if not path.resolve().is_relative_to(extracted.resolve()):
                    raise ValueError('Unsafe pack path')
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(archive.read(name))
    test('cl.betabridge.BetaJukeboxVoicesTest', extracted)
    test('cl.nostalgia.betabridge.BetaAtmosphereTest', extracted / 'fogs')
    if args.server:
        server = verify(args.server, LOCK['minecraftServer']['sha256'])
        test('cl.nostalgia.betabridge.BetaCraftingRecipesTest', server)
        test('cl.betabridge.BetaFishingBitesTest', server)
    if args.client:
        test('cl.nostalgia.betabridge.BetaChestBlockTest', verify(args.client, LOCK['minecraftClient']['sha256']))
    print('PASS. These tests do not replace authenticated iPhone gameplay testing.')
