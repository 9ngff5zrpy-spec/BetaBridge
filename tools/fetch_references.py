"""Fetch hash-pinned Mojang Bedrock sample metadata for local resource-pack rebuilding."""
import argparse
from concurrent.futures import ThreadPoolExecutor
import hashlib
import json
from pathlib import Path
import urllib.request
from common import ROOT, digest

def fetch(entry, destination):
    path = destination / entry['path']
    if path.exists() and entry.get('sha256') and digest(path) == entry['sha256']:
        return entry['path']
    request = urllib.request.Request(entry['url'], headers={'User-Agent': 'BetaBridge-reference-builder'})
    with urllib.request.urlopen(request, timeout=45) as response:
        data = response.read()
    if entry.get('sha256') and hashlib.sha256(data).hexdigest() != entry['sha256']:
        raise ValueError('Reference hash mismatch: ' + entry['path'])
    if entry.get('treeSha'):
        tree = json.loads(data)
        if tree.get('sha') != entry['treeSha'] or tree.get('truncated'):
            raise ValueError('Unexpected or incomplete official reference tree')
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data)
    return entry['path']

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--destination', type=Path, default=ROOT / '.cache/references')
    args = parser.parse_args()
    lock = json.loads((ROOT / 'data/resource-references.lock.json').read_text())
    with ThreadPoolExecutor(max_workers=6) as pool:
        results = list(pool.map(lambda entry: fetch(entry, args.destination), lock['files']))
    print('Verified official sample references:', len(results))
