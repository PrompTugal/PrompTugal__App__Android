#!/usr/bin/env python3
"""Build the android.app.promptugal.ai site into website/_site/.

One template, one source file per page (HTML or Markdown) under website/pages/. Every
fact about the app (application ID, SDK levels, permissions, languages, feature flags,
latest release, test counts, licences) is read from the repository by facts.py at build
time, never typed. Each page gets a Markdown twin at the same path for agents; llms.txt
and sitemap.xml list every page. The deploy workflow adds CNAME and build.json itself.

Standard library only. Usage: python3 website/build.py [--out DIR]
CI may set SITE_COMMIT to stamp the commit.
"""
import argparse
import html
import json
import re
import shutil
from pathlib import Path

import facts as factsmod
import markdown
import twin

SITE_URL = 'https://android.app.promptugal.ai/'
REPO_URL = factsmod.REPO_URL
WEBSITE = factsmod.WEBSITE
ROOT = factsmod.ROOT

ANDROID_NAMES = {'26': '8.0', '27': '8.1', '28': '9', '29': '10', '30': '11', '31': '12', '33': '13', '34': '14', '35': '15'}

SUMMIT_FEATURES = [  # (letter, name, what it will be, where it lives)
    ('A', 'RiskMandate inside the app', 'riskmandate.ai pages in an in-app view limited to the same address check, with native tabs and an offline copy for bad venue Wi-Fi.', 'site'),
    ('B', 'ABP in every layer', 'A native, animated, clearly labelled demonstration of one Agent Behaviour Policy stopping a planted instruction at six layers.', 'abpdemo'),
    ('C', 'This app’s own mandate', 'A live view of this app on this device: its reach (permissions, address allow-list), its mandate and the gap, and what it stores on the device.', 'mandate'),
    ('D', 'Stand mode', 'Screen pinning, screen kept on, and back to the home screen after a few idle minutes, switched on and off from the home screen.', 'stand'),
    ('F', 'What\u2019s new', 'Release articles inside the app: what changed in each version, with screenshots. (E, demo videos, waits for the videos.)', 'releases'),
]


def esc(text):
    return html.escape(str(text), quote=True)


def read_page(path):
    """Split a page source into its front matter (an HTML comment of `key: value` lines) and body."""
    text = path.read_text()
    match = re.match(r'<!--\n(.*?)\n-->\n', text, re.S)
    if not match:
        raise SystemExit(f'build: {path.name} has no front-matter comment')
    meta = dict(line.split(': ', 1) for line in match.group(1).splitlines())
    rel = path.relative_to(WEBSITE / 'pages').as_posix()
    meta.update(body=text[match.end():], source=rel, markdown=path.suffix == '.md')
    meta['path'] = rel[:-len(path.suffix)] + '.html'
    meta.setdefault('order', '99')
    return meta


def fill(text, tokens):
    def swap(match):
        if match.group(1) not in tokens:
            raise SystemExit(f'build: unknown token {{{{{match.group(1)}}}}}')
        return tokens[match.group(1)]
    return re.sub(r'\{\{(\w+)\}\}', swap, text)


def link_md(href):
    return href


def table(head, rows, classes=''):
    th = ''.join(f'<th scope="col">{h}</th>' for h in head)
    body = ''.join('<tr>' + ''.join(f'<td>{c}</td>' for c in row) + '</tr>' for row in rows)
    return f'<div class="table-wrap"><table{classes}><thead><tr>{th}</tr></thead><tbody>{body}</tbody></table></div>'


def permissions_table(facts):
    why = {'android.permission.INTERNET': 'Lets the app hand a riskmandate.ai address to your browser.',
           'android.permission.ACCESS_NETWORK_STATE': 'Lets the app say plainly when the device is offline. No network name or address is read.'}
    rows = [(f'<code>{esc(name)}</code>', esc(why.get(name, comment or 'See the manifest.'))) for name, comment in facts['permissions']]
    return table(['Permission (from the manifest)', 'What it is for'], rows)


def features_table(facts):
    """The summit features. A feature the source has not switched on is shown as in progress, whatever the plan says.

    A to C are Feature objects with an `enabled` flag. D (stand mode) is a host that wraps the app and has no flag:
    it is shown as in progress while StandHost.kt is the placeholder that renders the app unchanged."""
    flags = {f['id']: f for f in facts['features']}
    rows = []
    for letter, name, what, ident in SUMMIT_FEATURES:
        if ident == 'stand':
            on = facts['stand_host'] and not facts['stand_placeholder']
            source = ('<code>features/stand/StandHost.kt</code>: placeholder, renders the app unchanged' if facts['stand_placeholder']
                      else '<code>features/stand/StandHost.kt</code>' if facts['stand_host'] else 'no StandHost.kt yet')
        else:
            f = flags.get(ident)
            on = bool(f and f['enabled'])
            source = (f'<code>{esc(f["file"].split("ai/riskmandate/app/")[-1])}</code>: enabled = {str(f["enabled"]).lower()}' if f
                      else 'not in the feature list yet')
        rows.append(feature_row(ident, letter, f'<strong>{esc(name)}.</strong> {esc(what)}', on, source))
    planned = {s[3] for s in SUMMIT_FEATURES}
    for f in facts['features']:  # a feature in the source that this page has no text for yet is still listed, never hidden
        if f['id'] not in planned:
            rows.append(feature_row(f['id'], '+', f'<strong>{esc(f["name"])}.</strong> In the app\u2019s feature list; no description on this site yet.',
                                    f['enabled'], f'<code>{esc(f["file"].split("ai/riskmandate/app/")[-1])}</code>: enabled = {str(f["enabled"]).lower()}'))
    return ('<div class="table-wrap"><table><thead><tr><th scope="col"></th><th scope="col">Feature</th><th scope="col">Status</th>'
            '<th scope="col">What the source says</th></tr></thead><tbody>' + ''.join(rows) + '</tbody></table></div>')


def feature_row(ident, letter, text, on, source):
    status = '<span class="tag tag-ok">on</span>' if on else '<span class="tag tag-progress">in progress</span>'
    return f'<tr data-feature="{esc(ident)}"><td>{esc(letter)}</td><td>{text}</td><td>{status}</td><td>{source}</td></tr>'


def licence_summary(facts):
    lic = facts['licences']
    rows = [(esc(name), str(n)) for name, n in lic['by_licence']]
    return table(['Licence (as the library’s POM states it)', 'Libraries'], rows)


def licence_table(facts):
    rows = [(f'<code>{esc(a)}</code>', esc(l)) for a, l in facts['licences']['artifacts']]
    return '<details><summary>All {} libraries</summary>{}</details>'.format(len(rows), table(['Library', 'Licence'], rows))


def releases_table(facts):
    if not facts['releases']:
        return ('<p class="notice">No release tag (<code>v0.1.N</code>) was found in this checkout. '
                'If the site was built from a shallow clone, fetch the tags and build again.</p>')
    rows = [(f'<a href="{REPO_URL}/releases/tag/{r["tag"]}">{r["tag"]}</a>', str(r['run']),
             f'<a href="{REPO_URL}/commit/{r["commit"]}"><code>{r["commit"][:7]}</code></a>', esc(r['date']) + ' UTC')
            for r in facts['releases']]
    return table(['Release', 'CI run', 'Commit', 'Commit date'], rows)


def privacy_body(facts):
    """The body of docs/play/privacy.html (its <main>), with its to-do markers turned into plain statements."""
    source = factsmod.PRIVACY.read_text()
    main = re.search(r'<main id="main">(.*?)<footer>', source, re.S)
    if not main:
        raise SystemExit('build: docs/play/privacy.html has no <main id="main"> ... <footer>')
    text = main.group(1)
    text = re.sub(r'\s*<h1>.*?</h1>', '', text, flags=re.S)
    text = re.sub(r'\s*<p class="meta">.*?</p>', '', text, flags=re.S)
    repo = f'<a href="{REPO_URL}">{REPO_URL.split("://")[1]}</a>'

    def todo(match):
        marker = match.group(1)
        if 'repository URL' in marker:
            return repo
        if 'rights wording' in marker:
            return ''
        return '<em>(to be confirmed before the app is listed on Google Play)</em>'
    text = re.sub(r'<span class="todo">\[(.*?)\]</span>', todo, text, flags=re.S)
    if 'TODO' in text:
        raise SystemExit('build: a TODO is left in the privacy text')
    return text.strip()


def site_stamp(facts):
    return f'Built from commit <a href="{REPO_URL}/commit/{facts["commit"]}">{facts["commit"]}</a>.' if facts['commit'] else 'Local build.'


def png_size(path):
    """Width and height from a PNG's header, or None if it is not a PNG."""
    head = path.read_bytes()[:24]
    if len(head) == 24 and head[:8] == b'\x89PNG\r\n\x1a\n':
        return int.from_bytes(head[16:20], 'big'), int.from_bytes(head[20:24], 'big')
    return None


def article_pages(facts, out_assets):
    """One page per in-app release article. Screenshots are generated by CI and not committed: where one is
    present it is published with its real size, where it is not the caption stays and says so."""
    pages = []
    for a in facts['articles']:
        copies = {}

        def image(alt, src, a=a, copies=copies):
            name = src.split('/', 1)[-1]
            source = a['folder'] / src
            size = png_size(source) if src.startswith('screenshots/') and source.is_file() else None
            if not size:
                return f'<p class="muted">[Screenshot not in this build: {esc(alt)}]</p>'
            copies[name] = source
            return (f'<figure><img src="{esc(a["dir"])}/screenshots/{esc(name)}" alt="{esc(alt)}" width="{size[0]}" height="{size[1]}">'
                    f'<figcaption class="muted">{esc(alt)}</figcaption></figure>')
        body = ('<article class="band"><div class="wrap prose">\n'
                + markdown.render(a['body'], link_md, image)
                + ('\n<p class="muted">This article is also in the app, in European Portuguese.</p>' if a['has_pt'] else '')
                + '\n</div></article>')
        pages.append({'path': f'articles/{a["dir"]}.html', 'title': a['title'], 'description': a['summary'] or a['title'],
                      'kind': 'article', 'date': a['date'], 'order': '50', 'body': body, 'twin_text': a['body'].strip() + '\n',
                      'markdown': False, 'copies': copies,
                      'assets': a['dir']})
    return pages


def articles_list(pages):
    items = sorted((p for p in pages if p.get('kind') == 'article'), key=lambda p: p.get('date', ''), reverse=True)
    rows = ''.join(f'<li><a href="{esc(p["path"].removeprefix("articles/"))}"><strong>{esc(p["title"])}</strong></a><span>{esc(p.get("date", ""))}: {esc(p["description"])}</span></li>'
                   for p in items)
    return f'<ul class="article-list">{rows}</ul>' if rows else '<p>No articles yet.</p>'


def json_ld(page, facts, url):
    if page['path'] == 'index.html':
        sdk = facts['min_sdk']
        data = {'@context': 'https://schema.org', '@type': 'MobileApplication', 'name': 'RiskMandate for Android',
                'operatingSystem': f'Android {ANDROID_NAMES.get(sdk, "API " + sdk)} or later (API {sdk})',
                'applicationCategory': 'BusinessApplication', 'url': url, 'description': page['description'],
                'softwareVersion': facts['latest']['tag'][1:] if facts['latest'] else 'none released',
                'license': 'https://www.apache.org/licenses/LICENSE-2.0', 'codeRepository': REPO_URL,
                'identifier': facts['app_id']}
    elif page.get('kind') == 'article':
        data = {'@context': 'https://schema.org', '@type': 'TechArticle', 'headline': page['title'], 'url': url,
                'description': page['description'], 'datePublished': page.get('date', ''),
                'isPartOf': {'@type': 'WebSite', 'name': 'RiskMandate for Android', 'url': SITE_URL}}
    else:
        data = {'@context': 'https://schema.org', '@type': 'WebPage', 'name': page['title'], 'url': url,
                'description': page['description'], 'isPartOf': {'@type': 'WebSite', 'name': 'RiskMandate for Android', 'url': SITE_URL}}
    return json.dumps(data, indent=2, ensure_ascii=False)


def make_tokens(facts, pages):
    latest = facts['latest']
    perms = ', '.join(f'<code>{esc(n.split(".")[-1])}</code>' for n, _ in facts['permissions'])
    tokens = {
        'app_id': esc(facts['app_id']), 'min_sdk': facts['min_sdk'], 'target_sdk': facts['target_sdk'],
        'compile_sdk': facts['compile_sdk'], 'min_android': ANDROID_NAMES.get(facts['min_sdk'], 'API ' + facts['min_sdk']),
        'permission_count': str(len(facts['permissions'])), 'permission_names': perms,
        'permissions_table': permissions_table(facts),
        'locales': ' and '.join(facts['locales']),
        'latest_tag': latest['tag'] if latest else 'no release yet',
        'latest_version': latest['tag'][1:] if latest else 'none',
        'latest_run': str(latest['run']) if latest else 'none',
        'latest_commit': latest['commit'][:7] if latest else 'none',
        'latest_date': latest['date'] if latest else 'none',
        'release_count': str(len(facts['releases'])),
        'releases_table': releases_table(facts),
        'jvm_tests': str(facts['jvm_tests']), 'device_tests': str(facts['device_tests']),
        'features_table': features_table(facts),
        'licence_count': str(facts['licences']['count']), 'licence_flagged': str(facts['licences']['flagged']),
        'licence_summary': licence_summary(facts), 'licence_table': licence_table(facts),
        'privacy_body': privacy_body(facts), 'articles_list': articles_list(pages),
        'repo_url': REPO_URL, 'site_url': SITE_URL, 'commit': facts['commit'], 'commit_count': facts['commit_count'],
        'site_stamp': site_stamp(facts),
    }
    return tokens


def build(out):
    facts = factsmod.all_facts()
    pages = [read_page(p) for p in sorted((WEBSITE / 'pages').rglob('*')) if p.suffix in ('.html', '.md')]
    pages += article_pages(facts, None)
    pages.sort(key=lambda p: (int(p['order']), p['path']))
    tokens = make_tokens(facts, pages)
    template = (WEBSITE / 'template.html').read_text()

    if out.exists():
        shutil.rmtree(out)
    (out / 'assets').mkdir(parents=True)
    shutil.copy(WEBSITE / 'site.css', out / 'assets' / 'site.css')
    (out / 'assets' / 'icon.svg').write_text(factsmod.icon_svg())

    latest = facts['latest']
    llms = ['# RiskMandate for Android', '',
            f'> The RiskMandate app for Android (application ID {facts["app_id"]}, Android {tokens["min_android"]} or later), '
            'built by a team of AI agent sessions for Dinis Cruz. It opens riskmandate.ai. Open source, Apache-2.0. '
            f'Latest release: {tokens["latest_tag"]}. Facts are read from the source at build time.', '', '## Pages', '']
    sitemap = []
    for page in pages:
        path = page['path']
        root = '../' * path.count('/')
        url = SITE_URL + ('' if path == 'index.html' else path)
        twin_name = Path(path).with_suffix('.md').name
        # Release articles are shared with the app, which cannot fill tokens: they are published exactly as written.
        filled = page['body'] if page.get('kind') == 'article' and not page['markdown'] and 'twin_text' in page else fill(page['body'], tokens)
        if 'twin_text' in page:
            body, twin_text = filled, page['twin_text']
        elif page['markdown']:
            body = ('<article class="band"><div class="wrap prose">\n' + markdown.render(filled, link_md) + '\n</div></article>')
            twin_text = filled.strip() + '\n'
        else:
            body, twin_text = filled, twin.to_markdown(filled)
        nav = ''.join(f'<a href="{root}{p["path"]}"{" aria-current=\"page\"" if p is page else ""}>{esc(p["nav"])}</a>'
                      for p in pages if p.get('nav'))
        page_tokens = dict(tokens, title=esc(page['title']), description=esc(page['description']), canonical=url,
                           nav=nav, body=body, jsonld=json_ld(page, facts, url), twin=twin_name, root=root, robots='')
        target = out / path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(fill(template, page_tokens))
        target.with_suffix('.md').write_text(f'<!-- {url} -->\n\n' + twin_text)
        for name, source in page.get('copies', {}).items():
            (out / 'articles' / page['assets'] / 'screenshots').mkdir(parents=True, exist_ok=True)
            shutil.copy(source, out / 'articles' / page['assets'] / 'screenshots' / name)
        llms.append(f'- [{page["title"]}]({SITE_URL}{Path(path).with_suffix(".md").as_posix()}): {page["description"]}')
        sitemap.append(f'  <url><loc>{url}</loc></url>')

    llms += ['', '## Source', '', f'- [Source code (Apache-2.0)]({REPO_URL})', '- [riskmandate.ai](https://riskmandate.ai/): what the app opens', '']
    (out / 'llms.txt').write_text('\n'.join(llms))
    (out / 'sitemap.xml').write_text('<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n'
                                     + '\n'.join(sitemap) + '\n</urlset>\n')
    (out / 'robots.txt').write_text(f'User-agent: *\nAllow: /\nSitemap: {SITE_URL}sitemap.xml\n')
    (out / 'version.json').write_text(json.dumps(
        {'app_id': facts['app_id'], 'latest_release': latest['tag'] if latest else None, 'commit': facts['commit']}, indent=2) + '\n')
    return facts, pages


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument('--out', type=Path, default=WEBSITE / '_site')
    args = parser.parse_args()
    facts, pages = build(args.out)
    print(f'built {len(pages)} pages for {facts["app_id"]} ({facts["latest"]["tag"] if facts["latest"] else "no release"}) into {args.out}')


if __name__ == '__main__':
    main()
