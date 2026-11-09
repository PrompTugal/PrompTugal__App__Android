#!/usr/bin/env python3
"""Refuse a built site that breaks the rules. Exit 1 with one line per problem.

Checks every page in the output directory:
  * internal links, anchors and images resolve; every page is reachable from the home page
  * every page has a Markdown twin listed in llms.txt and in sitemap.xml
  * JSON-LD parses; the home page's softwareVersion and the releases page match the git tags
  * the facts match the source: application ID, minSdk, targetSdk, the permissions in the manifest
  * no summit feature is shown as "on" unless the source has switched it on
  * the privacy page has no unfinished marker and still says the app collects nothing
  * no scripts other than JSON-LD, no external stylesheets, no inline event handlers, no http links
  * one <h1>, a lang, a title and a description; every <img> has alt, width and height
  * no build tokens, banned words or dashes used as punctuation (em-dash) left behind
  * nothing secret-shaped, no email address (the reserved .example domain is allowed, for the worked bypass example), no Google project path in any output file
  * the files exist (robots.txt, llms.txt, sitemap.xml); CNAME is the deploy workflow's, not the build's

Standard library only. Usage: python3 website/validate.py [DIR]
"""
import json
import posixpath
import re
import sys
from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import urldefrag, urlparse

WEBSITE = Path(__file__).resolve().parent
sys.path.insert(0, str(WEBSITE))
import build  # noqa: E402
import facts as factsmod  # noqa: E402

BANNED = ('seamless', 'revolutionary', 'cutting-edge', 'game-changing', 'game changer', 'effortless',
          'blazing', 'unleash', 'world-class', 'best-in-class', 'supercharge', 'lorem ipsum', 'todo')
SECRETS = (('private key block', r'PRIVATE ' + r'KEY-----'), ('Google API key', r'AIza[0-9A-Za-z_-]{35}'),
           ('Google access token', r'ya29\.[0-9A-Za-z_-]{20,}'), ('GitHub token', r'gh[pousr]_[A-Za-z0-9]{36}|github_pat_[A-Za-z0-9_]{20,}'),
           ('service-account JSON', r'"type"\s*:\s*"service_account"'), ('Google project path', r'projects/\d{6,}'),
           ('AWS key id', r'(AKIA|ASIA)[0-9A-Z]{16}'), ('email address', r'[\w.+-]+@[\w-]+(?:\.[\w-]+)*\.(?!example\b)[A-Za-z]{2,}\b'))


class Page(HTMLParser):
    def __init__(self):
        super().__init__()
        self.links, self.images, self.ids, self.scripts, self.problems = [], [], set(), [], []
        self.h1 = 0
        self.lang = self.title = self.description = None
        self._script = None
        self._title = False

    def handle_starttag(self, tag, attrs):
        a = dict(attrs)
        if 'id' in a:
            self.ids.add(a['id'])
        for name in a:
            if name.startswith('on'):
                self.problems.append(f'inline event handler {name} on <{tag}>')
        if tag == 'html':
            self.lang = a.get('lang')
        elif tag == 'title':
            self._title = True
        elif tag == 'meta' and a.get('name') == 'description':
            self.description = a.get('content')
        elif tag == 'h1':
            self.h1 += 1
        elif tag == 'a' and 'href' in a:
            self.links.append(a['href'])
        elif tag == 'link' and a.get('rel') in ('stylesheet', 'icon'):
            if urlparse(a.get('href', '')).scheme:
                self.problems.append(f"external {a['rel']} {a['href']}")
            self.links.append(a.get('href', ''))
        elif tag == 'img':
            self.images.append(a.get('src', ''))
            for need in ('alt', 'width', 'height'):
                if a.get(need) is None:
                    self.problems.append(f"<img src={a.get('src')}> has no {need}")
        elif tag == 'script':
            if a.get('type') != 'application/ld+json' or 'src' in a:
                self.problems.append('a script that is not inline JSON-LD')
            self._script = ''

    def handle_endtag(self, tag):
        if tag == 'script' and self._script is not None:
            self.scripts.append(self._script)
            self._script = None
        elif tag == 'title':
            self._title = False

    def handle_data(self, data):
        if self._script is not None:
            self._script += data
        elif self._title:
            self.title = (self.title or '') + data


def visible(text):
    text = re.sub(r'<(script|style)\b.*?</\1>', ' ', text, flags=re.S)
    return re.sub(r'<[^>]+>', ' ', text)


def validate(out):
    problems = []
    pages = {p.relative_to(out).as_posix(): p for p in sorted(out.rglob('*.html'))}
    if 'index.html' not in pages:
        return ['no index.html']
    parsed = {}
    for name, path in pages.items():
        text = path.read_text()
        page = Page()
        page.feed(text)
        parsed[name] = page
        problems += [f'{name}: {p}' for p in page.problems]
        if page.h1 != 1:
            problems.append(f'{name}: has {page.h1} <h1> elements, wants 1')
        for field in ('lang', 'title', 'description'):
            if not getattr(page, field):
                problems.append(f'{name}: no {field}')
        if '{{' in text or '}}' in text:
            problems.append(f'{name}: a build token was left unfilled')
        text_only = visible(text)
        lowered = text_only.lower()
        for word in BANNED:
            if re.search(rf'\b{re.escape(word)}\b', lowered):
                problems.append(f'{name}: banned word "{word}"')
        if '—' in text_only:
            problems.append(f'{name}: an em-dash in the text')
        for block in page.scripts:
            try:
                json.loads(block)
            except ValueError as exc:
                problems.append(f'{name}: JSON-LD does not parse ({exc})')
        if not page.scripts:
            problems.append(f'{name}: no JSON-LD')

    # Secrets and private details, in every file that is published
    for path in sorted(out.rglob('*')):
        if path.is_file() and path.suffix in ('.html', '.md', '.txt', '.xml', '.json', '.css', '.svg'):
            text = path.read_text(errors='replace')
            for label, pattern in SECRETS:
                if re.search(pattern, text):
                    problems.append(f'{path.relative_to(out).as_posix()}: looks like a {label}')

    def resolve(name, href):
        target = urldefrag(href)[0]
        if not target:
            return name
        joined = posixpath.normpath(posixpath.join(posixpath.dirname(name), target))
        return '' if joined.startswith('..') else joined

    for name, page in parsed.items():
        for href in page.links + page.images:
            url = urlparse(href)
            if url.scheme or href.startswith('//') or href.startswith('mailto:'):
                if url.scheme == 'http':
                    problems.append(f'{name}: insecure link {href}')
                continue
            target, fragment = resolve(name, href), urldefrag(href)[1]
            if not target or not (out / target).exists():
                problems.append(f'{name}: broken link {href}')
            elif fragment and target.endswith('.html') and fragment not in parsed.get(target, Page()).ids:
                problems.append(f'{name}: no #{fragment} in {target}')

    seen, queue = set(), ['index.html']
    while queue:
        current = queue.pop()
        if current in seen or current not in parsed:
            continue
        seen.add(current)
        queue += [resolve(current, h) for h in parsed[current].links
                  if not urlparse(h).scheme and urldefrag(h)[0].endswith('.html')]
    for name in sorted(set(parsed) - seen):
        problems.append(f'{name}: not reachable from index.html')

    # Twins, llms.txt, sitemap.xml, deploy files
    llms = (out / 'llms.txt').read_text() if (out / 'llms.txt').exists() else ''
    sitemap = (out / 'sitemap.xml').read_text() if (out / 'sitemap.xml').exists() else ''
    for name in pages:
        twin = Path(name).with_suffix('.md').as_posix()
        if not (out / twin).exists():
            problems.append(f'{name}: no Markdown twin {twin}')
        if build.SITE_URL + twin not in llms:
            problems.append(f'{name}: twin not listed in llms.txt')
        url = build.SITE_URL + ('' if name == 'index.html' else name)
        if f'<loc>{url}</loc>' not in sitemap:
            problems.append(f'{name}: not in sitemap.xml')
    for name in ('llms.txt', 'sitemap.xml', 'robots.txt'):
        if not (out / name).exists():
            problems.append(f'no {name}')

    problems += check_facts(out, parsed)
    return problems


def check_facts(out, parsed):
    """Compare what the pages say with what the source says."""
    problems = []
    facts = factsmod.all_facts()
    read = lambda name: (out / name).read_text() if (out / name).exists() else ''
    index, features, releases, privacy = (read(n) for n in ('index.html', 'features.html', 'releases.html', 'privacy.html'))

    for needle, label in ((facts['app_id'], 'application ID'),):
        if needle not in features:
            problems.append(f'features.html: does not show the {label} {needle}')
    for key, label in (('min_sdk', 'minSdk'), ('target_sdk', 'targetSdk')):
        if not re.search(rf'<dt>{facts[key]}</dt><dd>{label}', features):
            problems.append(f'features.html: {label} {facts[key]} not shown as in the source')
    shown = set(re.findall(r'<td><code>(android\.permission\.[A-Z_]+)</code></td>', features))
    wanted = {n for n, _ in facts['permissions']}
    if shown != wanted:
        problems.append(f'features.html: permissions {sorted(shown)} differ from the manifest {sorted(wanted)}')

    # Summit features: "on" only when the source says so
    on_in_source = {f['id'] for f in facts['features'] if f['enabled']}
    rows = re.findall(r'<tr><td>([A-D])</td><td>.*?</td><td><span class="tag ([\w-]+)">', features, re.S)
    if len(rows) != 4:
        problems.append(f'features.html: lists {len(rows)} summit features, wants 4 (A to D)')
    for letter, tag in rows:
        ident = {s[0]: s[3] for s in build.SUMMIT_FEATURES}[letter]
        claims_on = tag == 'tag-ok'
        source_on = ident in on_in_source if ident != 'stand' else (facts['stand_host'] and not facts['stand_placeholder'])
        if claims_on and not source_on:
            problems.append(f'features.html: feature {letter} is shown as on but the source has not switched it on')

    latest = facts['latest']
    ld = re.search(r'<script type="application/ld\+json">\s*(.*?)\s*</script>', index, re.S)
    if ld:
        try:
            version = json.loads(ld.group(1)).get('softwareVersion')
        except ValueError:
            version = None
        expected = latest['tag'][1:] if latest else 'none released'
        if version != expected:
            problems.append(f'index.html: softwareVersion {version} is not the latest tag ({expected})')
    for release in facts['releases']:
        if release['tag'] not in releases:
            problems.append(f'releases.html: tag {release["tag"]} is missing')

    # Privacy
    if 'to be confirmed' not in privacy and 'TODO' in privacy:
        problems.append('privacy.html: an unfinished marker is left')
    if 'collects no personal data' not in privacy:
        problems.append('privacy.html: no longer says the app collects no personal data')
    for name, _ in facts['permissions']:
        if name.split('.')[-1] not in privacy:
            problems.append(f'privacy.html: does not mention the permission {name}')

    article = out / 'articles' / 'm1-ci-to-a-tablet-in-one-night.html'
    if not article.exists():
        problems.append('the article m1-ci-to-a-tablet-in-one-night is missing')
    else:
        text = article.read_text()
        for need in ('Dinis', 'unverified', facts['app_id']):
            if need not in text:
                problems.append(f'article: does not contain "{need}"')
    return problems


def main():
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else WEBSITE / '_site'
    problems = validate(out)
    for line in problems:
        print(line)
    print(f'{len(problems)} problem(s)' if problems else f'ok: {out} passes')
    sys.exit(1 if problems else 0)


if __name__ == '__main__':
    main()
