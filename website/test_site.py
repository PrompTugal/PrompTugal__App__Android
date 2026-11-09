#!/usr/bin/env python3
"""Tests for facts.py, build.py and validate.py. Each validator rule has a negative control:
a site broken on purpose that the validator must refuse. A rule that passes a broken site proves nothing.

Usage: python3 -m unittest website/test_site.py
"""
import json
import re
import shutil
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parent))
import build      # noqa: E402
import facts      # noqa: E402
import markdown   # noqa: E402
import twin       # noqa: E402
import validate   # noqa: E402

ROOT = facts.ROOT
REAL_FEATURES = facts.features  # the real parser, kept for the tests that parse fixture files

# The summit features move every hour as branches land, so SiteTest runs against this fixed fixture
# (nothing on, stand mode a placeholder). RealTreeTest checks the real tree, whatever state it is in.
FIXTURE_FEATURES = [{'name': n, 'id': i, 'enabled': False, 'file': f'app/src/main/java/ai/riskmandate/app/features/{i}/{n}.kt'}
                    for n, i in (('SiteFeature', 'site'), ('AbpDemoFeature', 'abpdemo'), ('MandateFeature', 'mandate'), ('ReleasesFeature', 'releases'))]


def fixture_patches(features=FIXTURE_FEATURES, stand=(True, True)):
    return [mock.patch.object(facts, 'features', return_value=features), mock.patch.object(facts, 'stand_host', return_value=stand)]


class RealTreeTest(unittest.TestCase):
    def test_the_real_tree_builds_and_validates_whatever_the_features_are(self):
        out = Path(tempfile.mkdtemp())
        try:
            build.build(out / 'site')
            self.assertEqual(validate.validate(out / 'site'), [])
        finally:
            shutil.rmtree(out)


class SiteTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.patches = fixture_patches()
        for patch in cls.patches:
            patch.start()
        cls.tmp = Path(tempfile.mkdtemp())
        cls.clean = cls.tmp / 'clean'
        cls.facts, cls.pages = build.build(cls.clean)

    @classmethod
    def tearDownClass(cls):
        shutil.rmtree(cls.tmp)
        for patch in cls.patches:
            patch.stop()

    def broken(self, name, edit):
        """A copy of the clean build with one file edited (or deleted when edit is None)."""
        out = self.tmp / f'broken-{self.id().rsplit(".", 1)[-1]}'
        if out.exists():
            shutil.rmtree(out)
        shutil.copytree(self.clean, out)
        if edit is None:
            (out / name).unlink()
        else:
            (out / name).write_text(edit((out / name).read_text()))
        return validate.validate(out)

    def assertRefused(self, problems, fragment):
        self.assertTrue(any(fragment in p for p in problems), f'expected "{fragment}" in {problems}')

    # --- the clean build ------------------------------------------------------

    def test_clean_build_passes(self):
        self.assertEqual(validate.validate(self.clean), [])

    def test_every_page_has_a_twin_and_the_deploy_files_exist(self):
        for page in self.pages:
            self.assertTrue((self.clean / Path(page['path']).with_suffix('.md')).exists(), page['path'])
        for name in ('llms.txt', 'sitemap.xml', 'robots.txt', 'version.json', 'assets/site.css', 'assets/icon.svg'):
            self.assertTrue((self.clean / name).exists(), name)
        self.assertFalse((self.clean / 'CNAME').exists())  # the deploy workflow adds it

    def test_required_pages_exist(self):
        paths = {p['path'] for p in self.pages}
        for need in ('index.html', 'features.html', 'install.html', 'privacy.html', 'open-source.html', 'releases.html',
                     'articles/index.html', 'articles/2026-11-09-m1-ci-to-a-tablet-in-one-night.html',
                     'articles/2026-11-09-web-summit-build.html'):
            self.assertIn(need, paths)

    # --- facts come from the source ------------------------------------------

    def test_facts_match_the_source_files(self):
        gradle = (ROOT / 'app' / 'build.gradle.kts').read_text()
        self.assertEqual(self.facts['app_id'], re.search(r'applicationId = "([^"]+)"', gradle).group(1))
        self.assertEqual(self.facts['min_sdk'], re.search(r'minSdk = (\d+)', gradle).group(1))
        self.assertEqual(self.facts['target_sdk'], re.search(r'targetSdk = (\d+)', gradle).group(1))
        manifest = (ROOT / 'app' / 'src' / 'main' / 'AndroidManifest.xml').read_text()
        self.assertEqual([n for n, _ in self.facts['permissions']], re.findall(r'<uses-permission android:name="([^"]+)"', manifest))

    def test_the_page_shows_what_the_source_says(self):
        features = (self.clean / 'features.html').read_text()
        self.assertIn(self.facts['app_id'], features)
        for name, _ in self.facts['permissions']:
            self.assertIn(name, features)

    def test_gradle_value_refuses_a_missing_setting(self):
        with mock.patch.object(facts, 'GRADLE', self.tmp / 'nothing.kts'):
            (self.tmp / 'nothing.kts').write_text('android { }')
            with self.assertRaises(SystemExit):
                facts.gradle_value('minSdk')

    def test_releases_are_the_v0_1_n_tags_newest_first(self):
        fake = 'v0.1.3\nv0.1.12\nv0.2.0\nlatest\nv0.1.7'
        with mock.patch.object(facts, 'git', side_effect=lambda *a: fake if a[0] == 'tag' else 'x'):
            self.assertEqual([r['tag'] for r in facts.releases()], ['v0.1.12', 'v0.1.7', 'v0.1.3'])

    def test_no_tags_is_said_plainly(self):
        out = self.tmp / 'notags'
        with mock.patch.object(facts, 'releases', return_value=[]):
            build.build(out)
        self.assertIn('No release tag', (out / 'releases.html').read_text())
        self.assertIn('no release yet', (out / 'index.html').read_text())

    def test_licences_come_from_the_licence_report(self):
        text = (ROOT / 'docs' / 'security' / 'licences.md').read_text()
        self.assertEqual(self.facts['licences']['count'], int(re.search(r'^(\d+) artifacts', text, re.M).group(1)))
        self.assertEqual(len(self.facts['licences']['artifacts']), self.facts['licences']['count'])
        self.assertIn(f'All {self.facts["licences"]["count"]} libraries', (self.clean / 'open-source.html').read_text())

    def test_test_counts_are_counted_from_the_source(self):
        folder = self.tmp / 'tests'
        folder.mkdir()
        (folder / 'A.kt').write_text('class A {\n  @Test fun a() {}\n  @Test\n  fun b() {}\n  // @Test in a comment\n}\n')
        self.assertEqual(facts.count_tests(folder), 2)

    def test_icon_is_built_from_the_launcher_drawable(self):
        svg = facts.icon_svg()
        self.assertIn('<path d="M40,32', svg)
        self.assertRegex(svg, r'fill="#[0-9A-Fa-f]{6}"')

    # --- summit features: in progress until the source says otherwise --------

    def test_summit_features_are_in_progress_today(self):
        enabled = [f for f in self.facts['features'] if f['enabled']]
        features = (self.clean / 'features.html').read_text()
        shown_ids = {s[3] for s in build.SUMMIT_FEATURES} | {f['id'] for f in self.facts['features']}
        stand_on = 0 if self.facts['stand_placeholder'] else 1
        self.assertEqual(features.count('tag-progress'), len(shown_ids) - len(enabled) - stand_on)

    def test_a_feature_switched_on_in_the_source_shows_as_on(self):
        on = [dict(f, enabled=True) for f in FIXTURE_FEATURES]
        out = self.tmp / 'on'
        with mock.patch.object(facts, 'features', return_value=on):
            build.build(out)
            self.assertEqual(validate.validate(out), [])  # the source (the patched one) says on, the page says on
        # stand mode is a placeholder in the fixture, so only the flagged features are on
        self.assertEqual((out / 'features.html').read_text().count('tag-ok'), len(on))
        # the same page checked against a source where they are off must be refused
        self.assertRefused(validate.validate(out), 'shown as on but the source has not switched it on')

    def test_feature_claimed_on_but_not_in_the_source_is_refused(self):
        problems = self.broken('features.html', lambda t: t.replace('tag tag-progress">in progress', 'tag tag-ok">on', 1))
        self.assertRefused(problems, 'shown as on but the source has not switched it on')

    def test_a_missing_feature_row_is_refused(self):
        problems = self.broken('features.html', lambda t: re.sub(r'<tr data-feature="stand">.*?</tr>', '', t, count=1, flags=re.S))
        self.assertRefused(problems, 'lists features')

    def test_a_feature_in_the_source_without_text_is_still_listed(self):
        extra = {'name': 'NewThing', 'id': 'newthing', 'enabled': False, 'file': 'app/src/main/java/ai/riskmandate/app/features/newthing/NewThing.kt'}
        with mock.patch.object(facts, 'features', return_value=self.facts['features'] + [extra]):
            out = self.tmp / 'extra'
            build.build(out)
        self.assertIn('data-feature="newthing"', (out / 'features.html').read_text())

    def test_enabled_forms_in_feature_objects(self):
        def parse(line):
            folder = self.tmp / 'ft'
            shutil.rmtree(folder, ignore_errors=True)
            (folder / 'x').mkdir(parents=True)
            (folder / 'Features.kt').write_text('val FEATURES = listOf(X)')
            (folder / 'x' / 'X.kt').write_text('object X : Feature {\n    override val id = "x"\n' + line + '\n}\n')
            with mock.patch.object(facts, 'FEATURES_KT', folder / 'Features.kt'), mock.patch.object(facts, 'FEATURES_DIR', folder), mock.patch.object(facts, 'ROOT', folder):
                return REAL_FEATURES()[0]['enabled']
        self.assertFalse(parse('    override val enabled = false'))
        self.assertTrue(parse('    override val enabled = true'))
        self.assertTrue(parse('    override val enabled: Boolean = true'))
        self.assertTrue(parse('    override val enabled get() = true'))
        self.assertFalse(parse('    override val enabled get() = BuildConfig.DEBUG'))  # not provably on
        self.assertTrue(parse(''))  # no override: the interface default is on

    # --- privacy ------------------------------------------------------------

    def test_privacy_has_no_unfinished_marker(self):
        text = (self.clean / 'privacy.html').read_text()
        self.assertNotIn('TODO', text)
        self.assertIn('collects no personal data', text)

    def test_privacy_body_refuses_a_marker_it_cannot_resolve(self):
        source = (ROOT / 'docs' / 'play' / 'privacy.html').read_text()
        with mock.patch.object(facts, 'PRIVACY', self.tmp / 'p.html'):
            (self.tmp / 'p.html').write_text(source.replace('<h1>', '<p>TODO plain</p><h1>', 1))
            with self.assertRaises(SystemExit):
                build.privacy_body(self.facts)

    def test_privacy_that_stops_saying_nothing_is_collected_is_refused(self):
        self.assertRefused(self.broken('privacy.html', lambda t: t.replace('collects no personal data', 'collects data')),
                           'collects no personal data')

    # --- the markdown pipeline ---------------------------------------------

    def test_markdown_twin_and_renderer(self):
        self.assertIn('| a | b |', twin.to_markdown('<table><thead><tr><th>a</th><th>b</th></tr></thead></table>'))
        self.assertIn('[x](y.md)', twin.to_markdown('<p><a href="y.html">x</a></p>'))
        self.assertIn('<h2 id="hello-there">Hello there</h2>', markdown.render('## Hello there'))
        self.assertNotIn('<script>', markdown.render('<script>alert(1)</script>'))

    def test_unknown_token_is_refused(self):
        with self.assertRaises(SystemExit):
            build.fill('{{nope}}', {})

    def test_article_is_a_markdown_page_with_a_twin_that_is_its_source(self):
        twin_text = (self.clean / 'articles' / '2026-11-09-m1-ci-to-a-tablet-in-one-night.md').read_text()
        self.assertIn('# M1: from an empty repository', twin_text)
        self.assertNotIn('{{', twin_text)
        self.assertIn(self.facts['app_id'], twin_text)

    def test_article_prose_has_no_em_dash(self):
        for path in facts.RELEASES_DIR.glob('*/article*.md'):
            self.assertNotIn('\u2014', path.read_text(), path.name)

    def test_the_sample_article_is_not_published_and_every_real_one_is(self):
        paths = {p['path'] for p in self.pages}
        for a in facts.release_articles():
            self.assertIn(f'articles/{a["dir"]}.html', paths)
        self.assertFalse(any(p.endswith('-sample.html') for p in paths))

    def test_front_matter_matches_the_apps_format(self):
        meta, body = facts.front_matter('---\ntitle: "Hello: world"\ndate: 2026-11-09\nsummary: \'Short.\'\nfeatures: [a, b]\nunknown: x\n---\n\n## Body\n')
        self.assertEqual((meta['title'], meta['date'], meta['summary']), ('Hello: world', '2026-11-09', 'Short.'))
        self.assertEqual(body.strip(), '## Body')
        self.assertEqual(facts.front_matter('no front matter')[0], {})

    def test_a_screenshot_is_published_when_present_and_a_caption_when_not(self):
        folder = self.tmp / 'rel' / '2026-11-10-x'
        (folder / 'screenshots').mkdir(parents=True)
        png = b'\x89PNG\r\n\x1a\n' + b'\x00\x00\x00\rIHDR' + (640).to_bytes(4, 'big') + (400).to_bytes(4, 'big') + b'\x00' * 20
        (folder / 'screenshots' / 'a.png').write_bytes(png)
        (folder / 'article.md').write_text('---\ntitle: "X"\ndate: 2026-11-10\nsummary: S.\n---\n\n# X\n\n![Present](screenshots/a.png)\n\n![Missing](screenshots/b.png)\n')
        with mock.patch.object(facts, 'RELEASES_DIR', self.tmp / 'rel'):
            out = self.tmp / 'img'
            build.build(out)
        page = (out / 'articles' / '2026-11-10-x.html').read_text()
        self.assertIn('width="640" height="400"', page)
        self.assertTrue((out / 'articles' / '2026-11-10-x' / 'screenshots' / 'a.png').exists())
        self.assertIn('Screenshot not in this build: Missing', page)
        self.assertEqual(validate.validate(out) and [p for p in validate.validate(out) if 'articles/2026-11-10-x' in p], [])

    def test_a_release_article_without_a_page_is_refused(self):
        name = facts.release_articles()[0]['dir']
        problems = self.broken(f'articles/{name}.html', None)
        self.assertRefused(problems, f'release article {name}')

    # --- every validator rule has a negative control ------------------------

    def test_broken_link_is_refused(self):
        self.assertRefused(self.broken('index.html', lambda t: t.replace('href="install.html"', 'href="gone.html"', 1)), 'broken link')

    def test_missing_anchor_is_refused(self):
        self.assertRefused(self.broken('index.html', lambda t: t.replace('href="install.html"', 'href="install.html#nope"', 1)), '#nope')

    def test_unreachable_page_is_refused(self):
        def edit(text):
            return text
        out = self.tmp / 'unreach'
        shutil.copytree(self.clean, out)
        shutil.copy(out / 'features.html', out / 'orphan.html')
        self.assertRefused(validate.validate(out), 'orphan.html: not reachable')

    def test_missing_twin_is_refused(self):
        self.assertRefused(self.broken('features.md', None), 'no Markdown twin')

    def test_page_missing_from_llms_is_refused(self):
        self.assertRefused(self.broken('llms.txt', lambda t: t.replace('features.md', 'x.md')), 'not listed in llms.txt')

    def test_page_missing_from_sitemap_is_refused(self):
        self.assertRefused(self.broken('sitemap.xml', lambda t: t.replace('features.html', 'x.html')), 'not in sitemap.xml')

    def test_script_is_refused(self):
        self.assertRefused(self.broken('index.html', lambda t: t.replace('</body>', '<script src="https://x.example/a.js"></script></body>')), 'not inline JSON-LD')

    def test_inline_handler_is_refused(self):
        self.assertRefused(self.broken('index.html', lambda t: t.replace('<body>', '<body onload="x()">')), 'inline event handler')

    def test_external_stylesheet_is_refused(self):
        self.assertRefused(self.broken('index.html', lambda t: t.replace('assets/site.css', 'https://cdn.example/site.css')), 'external stylesheet')

    def test_insecure_link_is_refused(self):
        self.assertRefused(self.broken('index.html', lambda t: t.replace('https://riskmandate.ai/', 'http://riskmandate.ai/', 1)), 'insecure link')

    def test_two_h1_is_refused(self):
        self.assertRefused(self.broken('index.html', lambda t: t.replace('<main id="main">', '<main id="main"><h1>Again</h1>')), '<h1>')

    def test_image_without_alt_is_refused(self):
        self.assertRefused(self.broken('index.html', lambda t: t.replace('<main id="main">', '<main id="main"><img src="assets/icon.svg" width="1" height="1">')), 'has no alt')

    def test_unfilled_token_is_refused(self):
        self.assertRefused(self.broken('index.html', lambda t: t.replace('<main id="main">', '<main id="main">{{app_id}}')), 'token was left')

    def test_banned_word_is_refused(self):
        self.assertRefused(self.broken('index.html', lambda t: t.replace('<main id="main">', '<main id="main"><p>A seamless app.</p>')), 'banned word')

    def test_absolute_data_claims_are_refused(self):
        for claim in ('The app collects nothing.', 'The app keeps no data.', 'It stores nothing.', 'The app sends nothing.', 'No data is kept.', 'So nothing is lost.'):
            problems = self.broken('index.html', lambda t, c=claim: t.replace('<main id="main">', f'<main id="main"><p>{c}</p>'))
            self.assertRefused(problems, 'absolute data claim')
        # the policy's own wording is allowed
        self.assertEqual(self.broken('index.html', lambda t: t.replace('<main id="main">', '<main id="main"><p>It sends nothing to its makers and collects no personal data.</p>')), [])

    def test_em_dash_is_refused(self):
        self.assertRefused(self.broken('index.html', lambda t: t.replace('<main id="main">', '<main id="main"><p>One — two.</p>')), 'em-dash')

    def test_broken_json_ld_is_refused(self):
        self.assertRefused(self.broken('index.html', lambda t: t.replace('"@context"', '"@context"::', 1)), 'JSON-LD does not parse')

    def test_wrong_software_version_is_refused(self):
        if not self.facts['latest']:
            self.skipTest('no tags in this checkout')
        self.assertRefused(self.broken('index.html', lambda t: re.sub(r'"softwareVersion": "[^"]+"', '"softwareVersion": "9.9.9"', t)), 'softwareVersion')

    def test_missing_release_is_refused(self):
        if not self.facts['latest']:
            self.skipTest('no tags in this checkout')
        tag = self.facts['latest']['tag']
        self.assertRefused(self.broken('releases.html', lambda t: t.replace(tag, 'v0.0.0')), f'tag {tag} is missing')

    def test_wrong_application_id_is_refused(self):
        self.assertRefused(self.broken('features.html', lambda t: t.replace(self.facts['app_id'], 'com.example.other')), 'application ID')

    def test_wrong_min_sdk_is_refused(self):
        self.assertRefused(self.broken('features.html', lambda t: t.replace(f'<dt>{self.facts["min_sdk"]}</dt><dd>minSdk', '<dt>99</dt><dd>minSdk')), 'minSdk')

    def test_extra_permission_is_refused(self):
        self.assertRefused(self.broken('features.html', lambda t: t.replace('<tbody>', '<tbody><tr><td><code>android.permission.CAMERA</code></td><td>x</td></tr>', 1)), 'permissions')

    def test_secret_shaped_text_is_refused(self):
        fake_key = 'AIza' + 'a' * 35
        self.assertRefused(self.broken('index.md', lambda t: t + fake_key), 'Google API key')
        self.assertRefused(self.broken('index.md', lambda t: t + '-----BEGIN PRIVATE ' + 'KEY-----'), 'private key')
        self.assertRefused(self.broken('index.md', lambda t: t + 'projects/123456789012/locations'), 'project path')

    def test_email_address_is_refused_but_the_example_domain_is_not(self):
        self.assertRefused(self.broken('index.md', lambda t: t + ' tester@gmail.com'), 'email address')
        self.assertEqual(self.broken('index.md', lambda t: t + ' https://riskmandate.ai:x@evil.example/'), [])

    def test_article_without_unverified_marks_is_refused(self):
        name = 'articles/2026-11-09-m1-ci-to-a-tablet-in-one-night.html'
        self.assertRefused(self.broken(name, lambda t: t.replace('unverified', 'checked')), 'unverified')


if __name__ == '__main__':
    unittest.main()
