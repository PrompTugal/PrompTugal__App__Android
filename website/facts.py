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
ICON_FOREGROUND = ROOT / 'app' / 'src' / 'main' / 'res' / 'drawable' / 'ic_launcher_foreground.xml'
COLORS = ROOT / 'app' / 'src' / 'main' / 'res' / 'values' / 'colors.xml'
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
        enabled = re.search(r'override val enabled = (true|false)', text)
        rows.append({'name': name, 'id': ident.group(1) if ident else name.lower(),
                     'enabled': (enabled.group(1) == 'true') if enabled else True,
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


def icon_svg():
    """The launcher mark as an SVG: the vector drawable's path on the launcher background colour."""
    path = re.search(r'android:pathData="([^"]+)"', ICON_FOREGROUND.read_text()).group(1)
    background = re.search(r'name="ic_launcher_background">(#\w+)<', COLORS.read_text()).group(1)
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="18 18 72 72" width="72" height="72">'
            f'<rect x="18" y="18" width="72" height="72" rx="16" fill="{background}"/>'
            f'<path d="{path}" fill="#ffffff"/></svg>\n')


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
        'commit': commit[:7],
        'commit_count': git('rev-list', '--count', 'HEAD') or '0',
    }
