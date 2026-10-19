"""Build only BetaBridge's own plugins/agent. Never replaces a running installation."""
import argparse
import os
from pathlib import Path
import shutil
import subprocess
import zipfile
from common import ROOT, LOCK, dependencies, java_tool, zip_write, digest

def compile_sources(sources, destination, classpath, release):
    if destination.exists():
        shutil.rmtree(destination)
    destination.mkdir(parents=True)
    subprocess.run([java_tool('javac'), '--release', str(release), '-proc:none', '-encoding', 'UTF-8',
                    '-cp', classpath, '-d', str(destination), *map(str, sources)], check=True)

def build(deps_dir=None, download=False, tests=False):
    deps = dependencies(deps_dir, download)
    cp = os.pathsep.join(map(str, deps.values()))
    output = ROOT / 'build'
    for module, jar in [('plugin', 'BetaBridge.jar'), ('extension', 'BetaHud.jar')]:
        sources = sorted((ROOT / module / 'src/main/java').rglob('*.java'))
        if module == 'extension':
            sources.append(ROOT / 'plugin/src/main/java/cl/betabridge/BetaJukeboxVoices.java')
        classes = output / module
        compile_sources(sources, classes, cp, 17 if module == 'plugin' else 21)
        with zipfile.ZipFile(output / jar, 'w') as archive:
            zip_write(archive, 'META-INF/MANIFEST.MF', b'Manifest-Version: 1.0\n\n')
            for directory in [classes, ROOT / module / 'src/main/resources']:
                for path in sorted(directory.rglob('*')):
                    if path.is_file():
                        zip_write(archive, path.relative_to(directory).as_posix(), path.read_bytes())
            zip_write(archive, 'LICENSE', (ROOT / 'LICENSE').read_bytes())
    classes = output / 'agent'
    compile_sources([ROOT / 'server-compat/Beta166ServerCompat.java'], classes, cp, 17)
    with zipfile.ZipFile(output / 'Beta166ServerCompat.jar', 'w') as archive:
        zip_write(archive, 'META-INF/MANIFEST.MF', b'Manifest-Version: 1.0\nPremain-Class: Beta166ServerCompat\n\n')
        for path in sorted(classes.glob('*.class')):
            zip_write(archive, path.name, path.read_bytes())
        with zipfile.ZipFile(deps['ViaProxy.jar']) as upstream:
            for name in sorted(upstream.namelist()):
                if name.startswith('org/objectweb/asm/') and name.endswith('.class'):
                    zip_write(archive, name, upstream.read(name))
        zip_write(archive, 'ASM-LICENSE.txt', (ROOT / 'licenses/ASM-LICENSE.txt').read_bytes())
        zip_write(archive, 'LICENSE', (ROOT / 'LICENSE').read_bytes())
    if tests:
        for module in ('plugin', 'extension'):
            test_cp = os.pathsep.join([cp, str(output / module)])
            compile_sources(sorted((ROOT / module / 'src/test/java').rglob('*.java')),
                            output / (module + '-tests'), test_cp, 21)
        names = ['cl.betabridge.LegacyIdentityTest', 'cl.betabridge.LegacyPlayerNamesTest',
                 'cl.betabridge.BedrockSkinCacheTest', 'cl.nostalgia.betabridge.Beta166PresentationTest',
                 'cl.nostalgia.betabridge.BetaFenceCollisionTest']
        test_cp = os.pathsep.join([cp, *map(str, [output / 'plugin', output / 'extension',
                                        output / 'plugin-tests', output / 'extension-tests'])])
        for name in names:
            subprocess.run([java_tool('java'), '-Djava.awt.headless=true', '-cp', test_cp, name], check=True)
    for name in ('BetaBridge.jar', 'BetaHud.jar', 'Beta166ServerCompat.jar'):
        print(name, digest(output / name))
    return deps

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--deps-dir', type=Path)
    parser.add_argument('--download', action='store_true', help='Fetch the two hash-pinned official dependencies.')
    parser.add_argument('--test', action='store_true', help='Run standalone tests; no Minecraft JAR needed.')
    args = parser.parse_args()
    build(args.deps_dir, args.download, args.test)
