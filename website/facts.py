"""Every fact the site states about the app, read from the repository at build time.

Nothing here is typed by hand: the application ID, SDK levels, permissions, languages,
feature flags, test counts, licences and releases all come from files and git.
Standard library only.
"""
import os
import re
import subprocess
from collections import Counter
from pathlib import Path

WEBSITE = Path(__file__).resolve().parent
ROOT = WEBSITE.parent
GRADLE = ROOT / 'app' / 'build.gradle.kts'
MANIFEST = ROOT / 'app' / 'src' / 'main' / 'AndroidManifest.xml'
LOCALES = ROOT / 'app' / 'src' / 'main' / 'res' / 'xml' / 'locales_config.xml'
FEATURES_KT = ROOT / 'app' / 'src' / 'main' / 'java' / 'ai' / 'riskmandate' / 'app' / 'features' / 'Features.kt'
FEATURES_DIR = FEATURES_KT.parent
RELEASES_DIR = ROOT / 'app' / 'src' / 'main' / 'assets' / 'releases'
LICENCES = ROOT / 'docs' / 'security' / 'licences.md'
PRIVACY = ROOT / 'docs' / 'play' / 'privacy.html'
TESTER_GUIDE = ROOT / 'docs' / 'play' / 'tester-guide-firebase.md'
REPO_URL = 'https://github.com/PrompTugal/PrompTugal__App__Android'

LOCALE_NAMES = {'en': 'English', 'pt-PT': 'European Portuguese'}


def gradle_value(name):
    text = GRADLE.read_text()
    match = re.search(rf'^\s*{name}\s*=\s*"?([\w.]+)"?\s*$', text, re.M)
    if not match:
        raise SystemExit(f'build: {name} not found in {GRADLE.name}')
    return match.group(1)


def permissions():
    """android.permission.* names in the manifest, in file order, each with the comment above it if any."""
    text = MANIFEST.read_text()
    found = []
    for match in re.finditer(r'(<!--(.*?)-->\s*)?<uses-permission\s+android:name="([\w.]+)"\s*/>', text, re.S):
        found.append((match.group(3), ' '.join((match.group(2) or '').split())))
    if not found:
        raise SystemExit('build: no uses-permission in the manifest')
    return found


def locales():
    names = re.findall(r'<locale android:name="([\w-]+)"', LOCALES.read_text())
    return [LOCALE_NAMES.get(n, n) for n in names]


def git(*args):
    try:
        return subprocess.run(['git', '-C', str(ROOT), *args], env=dict(os.environ, TZ='UTC'), capture_output=True, text=True, check=True).stdout.strip()
    except (OSError, subprocess.CalledProcessError):
        return ''


def releases():
    """Tags v0.1.N, newest first: {tag, run, commit, date}. The run number is N: CI names the release after its run."""
    rows = []
    for tag in git('tag', '--list', 'v0.1.*').split():
        match = re.fullmatch(r'v0\.1\.(\d+)', tag)
        if match:
            rows.append({'tag': tag, 'run': int(match.group(1)), 'commit': git('rev-list', '-n1', tag),
                         'date': git('log', '-1', '--format=%cd', '--date=format-local:%Y-%m-%d %H:%M', tag)})
    rows.sort(key=lambda r: -r['run'])
    return rows


def stand_host():
    """(exists, is_placeholder) for features/stand/StandHost.kt: a placeholder says so in its comment."""
    f = FEATURES_DIR / 'stand' / 'StandHost.kt'
    return f.exists(), f.exists() and 'Placeholder' in f.read_text()


def count_tests(folder):
    """@Test methods in Kotlin or Java files under folder (counted from the source, not a run result)."""
    total = 0
    for f in folder.rglob('*'):
        if f.suffix in ('.kt', '.java'):
            total += len(re.findall(r'^\s*@Test\b', f.read_text(), re.M))
    return total


def features():
    """The summit features: id, source file and whether it is turned on, from the Feature objects in the source."""
    listed = re.search(r'listOf\((.*?)\)', FEATURES_KT.read_text(), re.S)
    names = [n.strip() for n in listed.group(1).split(',') if n.strip()] if listed else []
    rows = []
    for name in names:
        files = [f for f in FEATURES_DIR.rglob('*.kt') if re.search(rf'object {name}\b', f.read_text())]
        if not files:
            continue
        text = files[0].read_text()
        ident = re.search(r'override val id = "(\w+)"', text)
        # A literal true or false only. Any other form (a getter, a condition) is not provably on, so it counts as off.
        enabled = re.search(r'override val enabled(?:\s*:\s*Boolean)?\s*(?:=|get\(\)\s*=)\s*(true|false)\b\s*$', text, re.M)
        rows.append({'name': name, 'id': ident.group(1) if ident else name.lower(),
                     'enabled': (enabled.group(1) == 'true') if enabled else ('override val enabled' not in text),
                     'file': files[0].relative_to(ROOT).as_posix()})
    return rows


def licences():
    """Parsed from docs/security/licences.md: artifact count, flagged count and rows per licence."""
    text = LICENCES.read_text()
    summary = re.search(r'^(\d+) artifacts\. Flagged: (\d+)\.', text, re.M)
    rows = [re.match(r'\|\s*([^|]+?)\s*\|\s*([^|]+?)\s*\|\s*([^|]*?)\s*\|\s*([^|]*?)\s*\|$', line)
            for line in text.splitlines() if re.match(r'\| [\w.-]+:[\w.-]+:', line)]
    artifacts = [(m.group(1), m.group(2)) for m in rows if m]
    if not summary or not artifacts:
        raise SystemExit('build: docs/security/licences.md has no summary line or table rows')
    return {'count': int(summary.group(1)), 'flagged': int(summary.group(2)),
            'artifacts': artifacts, 'by_licence': Counter(l for _, l in artifacts).most_common()}


def front_matter(text):
    """`---`, `key: value` lines, `---`, then the body: the same format the app reads. Values may be quoted."""
    lines = text.replace('\r\n', '\n').split('\n')
    if not lines or lines[0].strip() != '---':
        return {}, text
    end = next((i for i in range(1, len(lines)) if lines[i].strip() == '---'), None)
    if end is None:
        return {}, text
    meta = {}
    for line in lines[1:end]:
        key, sep, value = line.partition(':')
        if sep:
            value = value.strip()
            if len(value) > 1 and value[0] == value[-1] and value[0] in '"\'':
                value = value[1:-1]
            meta[key.strip()] = value
    return meta, '\n'.join(lines[end + 1:]).lstrip('\n')


def release_articles():
    """The in-app release articles (app/src/main/assets/releases/<YYYY-MM-DD>-<slug>/article.md), newest first.

    One source for the app and the site. A folder with no title or date, and the sample article, are skipped,
    as the app skips what it cannot parse."""
    found = []
    for folder in sorted(RELEASES_DIR.glob('*/article.md')) if RELEASES_DIR.exists() else []:
        meta, body = front_matter(folder.read_text())
        name = folder.parent.name
        date = meta.get('date') or (name[:10] if re.match(r'\d{4}-\d{2}-\d{2}', name) else '')
        if not meta.get('title') or not date or meta.get('sample', '').lower() == 'true':
            continue
        found.append({'dir': name, 'title': meta['title'], 'date': date, 'summary': meta.get('summary', ''),
                      'body': body, 'folder': folder.parent, 'has_pt': (folder.parent / 'article.pt-PT.md').exists()})
    found.sort(key=lambda a: (a['date'], a['dir']), reverse=True)
    return found


def _res_name(ref):
    """`@drawable/foo` or `@mipmap/foo` -> ('drawable', 'foo')."""
    kind, _, name = ref.lstrip('@').partition('/')
    return kind, name


def _color(res, name):
    for f in sorted((res / 'values').glob('*.xml')):
        match = re.search(rf'<color name="{re.escape(name)}">(#[0-9A-Fa-f]{{6,8}})</color>', f.read_text())
        if match:
            return match.group(1)
    raise SystemExit(f'build: colour {name} not found in {res / "values"}')


def icon_svg(manifest=None, res=None):
    """The launcher icon as an SVG, read from what the manifest says it is.

    Follows android:icon to the adaptive icon, takes its background colour and its foreground vector drawable,
    and draws the drawable's <group> transform and every path with its own fill (and evenOdd fill type) on the
    background, in the 72 dp the launcher shows through its mask. A plain one-path drawable works too."""
    from xml.etree import ElementTree as ET
    manifest = manifest or MANIFEST
    res = res or MANIFEST.parent / 'res'
    ns = '{http://schemas.android.com/apk/res/android}'
    icon = re.search(r'android:icon="(@\w+/\w+)"', manifest.read_text())
    if not icon:
        raise SystemExit('build: no android:icon in the manifest')
    kind, name = _res_name(icon.group(1))
    adaptive = ET.parse(res / kind / f'{name}.xml').getroot()
    background = adaptive.find('background').get(ns + 'drawable')
    foreground = adaptive.find('foreground').get(ns + 'drawable')
    bkind, bname = _res_name(background)
    fill = _color(res, bname) if bkind == 'color' else '#1B2A41'
    fkind, fname = _res_name(foreground)
    vector = ET.parse(res / fkind / f'{fname}.xml').getroot()
    width = float(vector.get(ns + 'viewportWidth', '108'))
    height = float(vector.get(ns + 'viewportHeight', '108'))

    def path(node):
        colour = node.get(ns + 'fillColor', '#FFFFFF')
        rule = ' fill-rule="evenodd"' if node.get(ns + 'fillType') == 'evenOdd' else ''
        return f'<path d="{node.get(ns + "pathData")}" fill="{colour}"{rule}/>'

    body = ''
    for node in vector:
        if node.tag == 'path':
            body += path(node)
        elif node.tag == 'group':
            tx, ty = node.get(ns + 'translateX', '0'), node.get(ns + 'translateY', '0')
            sx, sy = node.get(ns + 'scaleX', '1'), node.get(ns + 'scaleY', '1')
            body += f'<g transform="translate({tx} {ty}) scale({sx} {sy})">' + ''.join(path(p) for p in node if p.tag == 'path') + '</g>'
    if not body:
        raise SystemExit(f'build: no paths in {fname}.xml')
    x, y = (width - 72) / 2, (height - 72) / 2
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{x:g} {y:g} 72 72" width="72" height="72">'
            f'<rect x="{x:g}" y="{y:g}" width="72" height="72" rx="16" fill="{fill}"/>{body}</svg>\n')


def all_facts():
    rel = releases()
    commit = os.environ.get('SITE_COMMIT') or git('rev-parse', 'HEAD')
    lic = licences()
    return {
        'app_id': gradle_value('applicationId'),
        'min_sdk': gradle_value('minSdk'),
        'target_sdk': gradle_value('targetSdk'),
        'compile_sdk': gradle_value('compileSdk'),
        'permissions': permissions(),
        'locales': locales(),
        'features': features(),
        'stand_host': stand_host()[0],
        'stand_placeholder': stand_host()[1],
        'releases': rel,
        'latest': rel[0] if rel else None,
        'jvm_tests': count_tests(ROOT / 'app' / 'src' / 'test'),
        'device_tests': count_tests(ROOT / 'app' / 'src' / 'androidTest'),
        'licences': lic,
        'articles': release_articles(),
        'commit': commit[:7],
        'commit_count': git('rev-list', '--count', 'HEAD') or '0',
    }
