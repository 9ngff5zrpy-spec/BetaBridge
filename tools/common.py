"""Shared pinned-input and packaging helpers. No Minecraft downloads."""
from pathlib import Path
import hashlib
import json
import os
import shutil
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
LOCK = json.loads((ROOT / 'dependencies.lock.json').read_text())

def digest(path):
    with Path(path).open('rb') as stream:
        value = hashlib.sha256()
        for chunk in iter(lambda: stream.read(1024 * 1024), b''):
            value.update(chunk)
    return value.hexdigest()

def verify(path, expected):
    path = Path(path).resolve()
    if not path.is_file() or digest(path) != expected:
        raise ValueError('Missing or unexpected SHA-256: ' + str(path))
    return path

def java_tool(name):
    home = os.environ.get('JAVA_HOME')
    executable = name + ('.exe' if os.name == 'nt' else '')
    candidate = Path(home) / 'bin' / executable if home else None
    found = str(candidate) if candidate and candidate.is_file() else shutil.which(name)
    if not found:
        raise RuntimeError('Install JDK 21 and put ' + name + ' on PATH (or set JAVA_HOME).')
    return found

def dependencies(directory=None, download=False):
    directory = Path(directory).resolve() if directory else ROOT / '.cache/dependencies'
    directory.mkdir(parents=True, exist_ok=True)
    found = {}
    for asset in LOCK['dependencies']:
        path = directory / asset['name']
        if not path.exists():
            if not download:
                raise ValueError('Missing ' + str(path) + '; pass --download or --deps-dir.')
            temporary = path.with_suffix('.download')
            request = urllib.request.Request(asset['url'], headers={'User-Agent': 'BetaBridge/' + LOCK['version']})
            try:
                with urllib.request.urlopen(request, timeout=60) as source, temporary.open('wb') as target:
                    shutil.copyfileobj(source, target)
                verify(temporary, asset['sha256'])
                temporary.replace(path)
            finally:
                if temporary.exists():
                    temporary.unlink()
        found[asset['name']] = verify(path, asset['sha256'])
    return found

def zip_write(archive, name, data):
    info = zipfile.ZipInfo(name, date_time=(2026, 1, 1, 0, 0, 0))
    info.compress_type = zipfile.ZIP_DEFLATED
    info.external_attr = (0o755 if name.endswith(('.sh', '.command')) else 0o644) << 16
    archive.writestr(info, data)
