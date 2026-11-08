#!/usr/bin/env bash
# CI licence gate: runs scripts/licences.sh (owned by security.android) and fails if any runtime
# dependency's licence is not Apache-2.0, MIT or BSD, or is unknown, unless it is listed in
# scripts/licences-allow.txt (one "group:artifact" per line, with a reason in a comment).
# licences.sh itself also accepts ISC and public domain and never exits non-zero; this wrapper is stricter.
set -euo pipefail
cd "$(dirname "$0")/.."
bash scripts/licences.sh
python3 -I - docs/security/licences.md scripts/licences-allow.txt <<'PY'
import re, sys
table, allow_file = sys.argv[1:3]
allow = set()
for l in open(allow_file, encoding='utf-8'):
    l = l.split('#', 1)[0].strip()
    if l: allow.add(l)
OK = re.compile(r'apache|\bmit\b|bsd', re.I)
bad, n = [], 0
for line in open(table, encoding='utf-8'):
    if not line.startswith('| ') or line.startswith('| Artifact') or line.startswith('|---'):
        continue
    n += 1
    cols = [c.strip() for c in line.strip().strip('|').split('|')]
    coord, name, url = cols[0], cols[1], cols[2]
    ga = ':'.join(coord.split(':')[:2])
    if ga in allow: continue
    if name == 'UNKNOWN' or not (OK.search(name) or OK.search(url)):
        bad.append(f'{coord}: {name}')
if n == 0:
    sys.exit('licence gate: no dependencies found in the table (did licences.sh fail to resolve?)')
if bad:
    print('Licence gate FAILED, not Apache-2.0, MIT or BSD:')
    for b in bad: print('  -', b)
    sys.exit(1)
print(f'Licence gate passed: {n} artifacts, all Apache-2.0, MIT or BSD (allow-list entries: {len(allow)})')
PY
