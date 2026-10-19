"""Build deterministic public archives from an explicit allowlist, never from a runtime."""
from pathlib import Path
import json
import zipfile
from common import ROOT, LOCK, digest, verify, zip_write

ROOT_FILES = ['README.md', 'LICENSE', 'VERSION', '.gitignore', 'dependencies.lock.json',
              'THIRD_PARTY_NOTICES.md', 'CONTRIBUTING.md', 'SECURITY.md', 'install.py',
              'INSTALL-FIRST.txt', 'Install-BetaBridge.sh', 'Install-BetaBridge.command', 'Install-BetaBridge.bat']
FOLDERS = {'plugin/src': {'.java', '.json', '.yml', '.properties'},
           'extension/src': {'.java', '.json', '.yml', '.properties'},
           'server-compat': {'.java'}, 'tools': {'.py'}, 'data': {'.json'},
           'docs': {'.md', '.png'}, 'licenses': {'.txt'}, '.github': {'.md', '.yml'}}

def source_files():
    selected = {ROOT / name for name in ROOT_FILES}
    for folder, extensions in FOLDERS.items():
        for path in (ROOT / folder).rglob('*'):
            if path.is_file() and path.suffix in extensions and '__pycache__' not in path.parts:
                selected.add(path)
    for path in selected:
        if path.is_symlink():
            raise ValueError('Public input cannot be a symlink: ' + str(path))
        if path.suffix in {'.java', '.py', '.md', '.json', '.yml', '.properties'}:
            text = path.read_text()
            for private in ['/' + 'Users' + '/', str(Path.home())]:
                if private in text:
                    raise ValueError('Private workstation data in: ' + str(path.relative_to(ROOT)))
    return sorted(selected)

def package():
    destination = ROOT / 'dist'
    destination.mkdir(exist_ok=True)
    files = source_files()
    pack = verify(ROOT / LOCK['pack']['path'], LOCK['pack']['sha256'])
    binaries = [ROOT / 'build' / name for name in
                ('BetaBridge.jar', 'BetaHud.jar', 'Beta166ServerCompat.jar')]
    for path in binaries:
        if not path.is_file():
            raise ValueError('Build first: ' + path.name)
    prefix = 'BetaBridge-' + LOCK['version']
    for kind in ('source', 'community-preview'):
        path = destination / (prefix + '-' + kind + '.zip')
        with zipfile.ZipFile(path, 'w') as archive:
            for item in files:
                zip_write(archive, prefix + '/' + item.relative_to(ROOT).as_posix(), item.read_bytes())
            if kind == 'community-preview':
                for item in [*binaries, pack]:
                    zip_write(archive, prefix + '/' + item.relative_to(ROOT).as_posix(), item.read_bytes())
        with zipfile.ZipFile(path) as archive:
            if archive.testzip() is not None:
                raise ValueError('Broken archive: ' + str(path))
        print(path.name, digest(path))
    target = destination / pack.name
    target.write_bytes(pack.read_bytes())
    manifest = {'version': LOCK['version'], 'experimental': True, 'builtWith': 'OpenAI Codex',
                'profile': LOCK['profile'], 'testedBedrock': LOCK['testedBedrock'],
                'files': {path.name: {'sha256': digest(path), 'bytes': path.stat().st_size}
                          for path in sorted(destination.iterdir()) if path.suffix in {'.zip', '.mcpack'}}}
    (destination / 'release-manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
    (destination / 'SHA256SUMS.txt').write_text(''.join(
        info['sha256'] + '  ' + name + '\n' for name, info in manifest['files'].items()))
    print('Pack, matching source, checksums and release manifest prepared. Nothing published.')

if __name__ == '__main__':
    package()
