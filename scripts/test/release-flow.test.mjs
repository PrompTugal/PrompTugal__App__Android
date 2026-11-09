import test from 'node:test';
import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { mkdtempSync, writeFileSync, mkdirSync, rmSync, readFileSync, copyFileSync, chmodSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';

const scripts = resolve(import.meta.dirname, '..');
const root = resolve(scripts, '..');
function repo() {
  const dir = mkdtempSync(join(tmpdir(), 'flow-'));
  const g = (...a) => execFileSync('git', a, { cwd: dir, encoding: 'utf8' }).trim();
  g('init', '-q', '-b', 'dev'); g('config', 'user.email', 't@example.invalid'); g('config', 'user.name', 't'); g('config', 'commit.gpgsign', 'false');
  const commit = (file, msg) => { mkdirSync(join(dir, file, '..'), { recursive: true }); writeFileSync(join(dir, file), msg + Math.random()); g('add', '-A'); g('commit', '-q', '-m', msg); return g('rev-parse', 'HEAD'); };
  return { dir, g, commit, done: () => rmSync(dir, { recursive: true, force: true }) };
}
const bash = (dir, script, args = [], env = {}) => { try { return { code: 0, out: execFileSync('bash', [join(scripts, script), ...args], { cwd: dir, encoding: 'utf8', env: { ...process.env, ...env }, stdio: ['ignore', 'pipe', 'pipe'] }) }; } catch (e) { return { code: e.status, out: String(e.stdout), err: String(e.stderr) }; } };

// security.android's verifier is copied into scripts/ (docs/security/approvals.md: the check runs from main's copy).
const importLine = (t) => t.replace("from './approval-lib.mjs'", "from './verify-approval-lib.mjs'");
test('the copy of the verifier in scripts/ is identical to security.android\'s original (only the import path differs)', () => {
  const orig = (f) => readFileSync(join(root, 'docs/security/approvals', f), 'utf8');
  assert.equal(readFileSync(join(scripts, 'verify-approval-lib.mjs'), 'utf8'), orig('approval-lib.mjs'));
  assert.equal(readFileSync(join(scripts, 'verify-approval.mjs'), 'utf8'), importLine(orig('verify-approval.mjs')));
  // the human-promotion wrapper may differ only by the one flag it passes
  const human = readFileSync(join(scripts, 'verify-approval-human.mjs'), 'utf8');
  assert.match(human, /allowTrustPathChange: true/);
  assert.match(human, /from '\.\/verify-approval-lib\.mjs'/);
});

test('security.android\'s self-test passes against the copy in scripts/ (same code, same 15+ cases)', () => {
  const dir = mkdtempSync(join(tmpdir(), 'selftest-'));
  try {
    copyFileSync(join(scripts, 'verify-approval-lib.mjs'), join(dir, 'approval-lib.mjs'));
    copyFileSync(join(root, 'docs/security/approvals/selftest.mjs'), join(dir, 'selftest.mjs'));
    const out = execFileSync('node', [join(dir, 'selftest.mjs')], { encoding: 'utf8' });
    assert.match(out, /all ok/);
  } finally { rmSync(dir, { recursive: true, force: true }); }
});

test('promote-select: the newest dev commit the verifier approves; none if none; a crash or non-zero is a no (fail closed)', () => {
  const r = repo(); try {
    r.commit('README.md', 'base'); r.g('branch', 'main');
    const a = r.commit('app/a.txt', 'a'); const b = r.commit('app/b.txt', 'b'); const c = r.commit('app/c.txt', 'c');
    const stub = (body) => { const f = join(r.dir, '..', 'stub-' + Math.random().toString(36).slice(2) + '.sh'); writeFileSync(f, '#!/usr/bin/env bash\n' + body); chmodSync(f, 0o755); return f; };
    // approves only commit b: args are --repo . --main M --tip T, so the tip is $6
    const approveB = stub(`[ "$6" = "${b}" ] && { echo '{"ok":true}'; exit 0; }; echo '{"ok":false,"reason":"no"}'; exit 1`);
    const sel = (verifier, env = {}) => bash(r.dir, 'promote-select.sh', ['main', 'dev'], { VERIFIER: verifier, ...env });
    assert.equal(sel(approveB).out.trim(), b);
    const approveAC = stub(`case "$6" in ${a}|${c}) exit 0;; esac; exit 1`);
    assert.equal(sel(approveAC).out.trim(), c, 'the newest approved one wins');
    const never = stub('exit 1'); const none = sel(never);
    assert.equal(none.code, 1); assert.match(none.err, /no approved commit/); assert.match(none.err, new RegExp(c.slice(0, 7)));
    const crash = stub('echo boom >&2; exit 139'); assert.equal(sel(crash).code, 1, 'a crash is not an approval');
    const missing = sel('/nonexistent/verifier'); assert.equal(missing.code, 1, 'a missing verifier is not an approval');
    assert.equal(sel(approveB, { MAX: '1' }).code, 1, 'only the newest MAX commits are tried');
  } finally { r.done(); }
});

test('changed-build-paths: docs-only is no, app and workflow and gradle files are yes', () => {
  const r = repo(); try {
    const base = r.commit('README.md', 'base');
    const docs = r.commit('docs/x.md', 'docs'); const board = r.commit('board/status.json', 'board'); const dev = r.commit('devices/amapi.mjs', 'devices'); const web = r.commit('website/index.html', 'site');
    assert.equal(bash(r.dir, 'changed-build-paths.sh', [base, web]).code, 1);
    for (const [file, name] of [['app/src/Main.kt', 'app'], ['.github/workflows/android.yml', 'workflow'], ['gradle/libs.toml', 'gradle'], ['build.gradle.kts', 'root kts'], ['app/build.gradle.kts', 'app kts'], ['gradle.properties', 'props'], ['gradlew', 'wrapper'], ['scripts/x.sh', 'scripts']]) {
      const before = r.g('rev-parse', 'HEAD'); r.commit(file, name);
      const res = bash(r.dir, 'changed-build-paths.sh', [before, 'HEAD']);
      assert.equal(res.code, 0, name); assert.match(res.out, new RegExp(file.replace(/[.]/g, '\\.')));
    }
    void docs; void board; void dev;
    assert.equal(bash(r.dir, 'changed-build-paths.sh', ['0'.repeat(40), 'HEAD']).code, 0, 'unknown base: assume changed');
    assert.equal(bash(r.dir, 'changed-build-paths.sh', ['f'.repeat(40), 'HEAD']).code, 0, 'missing commit: assume changed');
  } finally { r.done(); }
});

test('find-release-tag: the newest tag with no build change since; none when the app changed after every tag', () => {
  const r = repo(); try {
    r.commit('app/a.txt', 'app 1'); r.g('tag', 'v0.1.10');
    r.commit('app/b.txt', 'app 2'); r.g('tag', 'v0.1.11');
    const docs = r.commit('docs/x.md', 'docs only');
    assert.equal(bash(r.dir, 'find-release-tag.sh', [docs]).out.trim(), 'v0.1.11');
    const app3 = r.commit('app/c.txt', 'app 3 (no release)');
    assert.equal(bash(r.dir, 'find-release-tag.sh', [app3]).code, 1, 'built code with no release must not be uploaded');
    r.g('tag', 'v0.1.12');
    assert.equal(bash(r.dir, 'find-release-tag.sh', [app3]).out.trim(), 'v0.1.12');
    // v0.1.9 is higher than v0.1.10 only if sorted as text: the sort must be by version
    r.commit('docs/y.md', 'docs 2'); r.g('tag', 'v0.1.9');
    const head = r.g('rev-parse', 'HEAD');
    assert.equal(bash(r.dir, 'find-release-tag.sh', [head]).out.trim(), 'v0.1.12');
  } finally { r.done(); }
});

test('find-release-tag: tests-only and CI-only changes keep the build; shipped changes (even app tests, which make the screenshots) do not', () => {
  const r = repo(); try {
    r.commit('app/src/main/A.kt', 'app'); r.g('tag', 'v0.1.20');
    r.commit('app/src/androidTest/SmokeTest.kt', 'instrumented test'); r.commit('scripts/smoke-test.sh', 'ci script'); r.commit('.github/workflows/android.yml', 'workflow');
    let head = r.g('rev-parse', 'HEAD');
    assert.equal(bash(r.dir, 'find-release-tag.sh', [head]).out.trim(), 'v0.1.20', 'androidTest, scripts and workflows are not shipped');
    // the wide rule (does CI build?) still sees them
    assert.equal(bash(r.dir, 'changed-build-paths.sh', ['v0.1.20', head]).code, 0);
    assert.equal(bash(r.dir, 'changed-build-paths.sh', ['--shipped', 'v0.1.20', head]).code, 1);
    for (const f of ['app/src/main/B.kt', 'app/src/test/ScreenshotTest.kt', 'scripts/release-screenshots.sh', 'app/build.gradle.kts', 'gradle/libs.versions.toml', 'gradle.properties']) {
      const before = r.g('rev-parse', 'HEAD'); r.commit(f, 'shipped: ' + f);
      assert.equal(bash(r.dir, 'changed-build-paths.sh', ['--shipped', before, 'HEAD']).code, 0, f);
      assert.equal(bash(r.dir, 'find-release-tag.sh', ['HEAD']).code, 1, f + ' needs its own release');
      r.g('reset', '-q', '--hard', before);
    }
    head = r.g('rev-parse', 'HEAD');
    assert.equal(bash(r.dir, 'find-release-tag.sh', [head]).out.trim(), 'v0.1.20');
  } finally { r.done(); }
});

test('changed-since-release: compares with the latest release, so a cancelled run cannot drop a change', () => {
  const r = repo(); try {
    r.commit('app/src/main/A.kt', 'built and released'); r.g('tag', 'v0.1.30');
    const cancelled = r.commit('app/src/main/B.kt', 'a real app change whose run was cancelled by the next push');
    const docs = r.commit('docs/debrief.md', 'debrief text only');
    // The push-to-push diff of the last push (docs only) says "no change"; against the release it is a change.
    assert.equal(bash(r.dir, 'changed-build-paths.sh', [cancelled, docs]).code, 1, 'the old rule: this push alone changed nothing');
    const since = bash(r.dir, 'changed-since-release.sh', [docs]);
    assert.equal(since.code, 0); assert.match(since.out, /app\/src\/main\/B\.kt/); assert.match(since.out, /v0\.1\.30/);
    // once that change has been released, a docs-only push changes nothing
    r.g('tag', 'v0.1.31', docs);
    assert.equal(bash(r.dir, 'changed-since-release.sh', [docs]).code, 1);
    const docs2 = r.commit('board/status.json', 'board only');
    assert.equal(bash(r.dir, 'changed-since-release.sh', [docs2]).code, 1, 'docs on top of a release: nothing to build');
    // tests and CI scripts count here (CI must run them), unlike in the shipped rule
    r.commit('app/src/androidTest/SmokeTest.kt', 'smoke fix');
    assert.equal(bash(r.dir, 'changed-since-release.sh', ['HEAD']).code, 0);
  } finally { r.done(); }
});

test('changed-since-release: no release tag yet, or a tag that is not an ancestor, counts as changed (fail towards building)', () => {
  const r = repo(); try {
    r.commit('README.md', 'base');
    const none = bash(r.dir, 'changed-since-release.sh', ['HEAD']);
    assert.equal(none.code, 0); assert.match(none.out, /no release tag/);
    r.g('checkout', '-q', '-b', 'side'); r.commit('app/x.txt', 'side'); r.g('tag', 'v0.1.99'); r.g('checkout', '-q', 'dev');
    const docs = r.commit('docs/y.md', 'docs on dev');
    assert.equal(bash(r.dir, 'changed-since-release.sh', [docs]).code, 0, 'a tag on another branch is not a release of this history');
  } finally { r.done(); }
});

test('resolve-commit: a typed prefix means the commit with that NAME, never a ref that looks like it (R-rel-9)', () => {
  const r = repo(); try {
    const a = r.commit('README.md', 'approved commit A'); const b = r.commit('app/x.txt', 'other commit B');
    const short = a.slice(0, 9);
    assert.equal(bash(r.dir, 'resolve-commit.sh', [short]).out.trim(), a, 'short form');
    assert.equal(bash(r.dir, 'resolve-commit.sh', [a]).out.trim(), a, 'full form');
    // a branch and a tag named like the short SHA, both pointing at B: plain `git rev-parse` would pick B
    r.g('branch', short, b); r.g('tag', a.slice(0, 8), b);
    assert.equal(r.g('rev-parse', short), b, 'precondition: git itself prefers the ref');
    assert.equal(bash(r.dir, 'resolve-commit.sh', [short]).out.trim(), a, 'the ref cannot redirect the input');
    assert.equal(bash(r.dir, 'resolve-commit.sh', [a.slice(0, 8)]).out.trim(), a, 'nor can the tag');
    // refused: ref names, too short, upper case, unknown, shell metacharacters, empty
    for (const bad of ['dev', 'main', 'HEAD', a.slice(0, 6), a.toUpperCase().slice(0, 12), 'deadbeefdeadbeef', `${short}; echo pwned`, '']) {
      assert.equal(bash(r.dir, 'resolve-commit.sh', [bad]).code === 0, false, `refused: "${bad}"`);
    }
  } finally { r.done(); }
});

test('resolve-commit: an abbreviation that names more than one commit is refused; trees do not count', () => {
  const r = repo(); try {
    // 700 empty commits: two of them share their first 4 hex digits (birthday bound at 16 bits is about 300)
    r.commit('README.md', 'base');
    const seen = new Map(); let pair = null; const shas = [];
    for (let i = 0; i < 700 && !pair; i++) {
      r.g('commit', '-q', '--allow-empty', '-m', `empty ${i}`);
      const h = r.g('rev-parse', 'HEAD'); shas.push(h);
      const k = h.slice(0, 4);
      if (seen.has(k)) pair = [seen.get(k), h]; else seen.set(k, h);
    }
    assert.ok(pair, 'precondition: two commits share a 4-digit prefix');
    const k = pair[0].slice(0, 4);
    // MIN_HEX=4 is a test-only override (git cannot disambiguate fewer than 4 digits); the workflow never sets it
    const amb = bash(r.dir, 'resolve-commit.sh', [k], { MIN_HEX: '4' });
    assert.equal(amb.code, 1); assert.match(amb.err, /names 2 commits/);
    // a 4-digit prefix that only one commit has resolves; the same input is refused under the real minimum of 7
    const unique = shas.find((h) => shas.filter((x) => x.startsWith(h.slice(0, 4))).length === 1);
    assert.equal(bash(r.dir, 'resolve-commit.sh', [unique.slice(0, 4)], { MIN_HEX: '4' }).out.trim(), unique);
    assert.equal(bash(r.dir, 'resolve-commit.sh', [unique.slice(0, 4)]).code, 1, 'real minimum is 7');
    // the 7-digit prefix of either commit of the pair is almost certainly unique, and resolves to ITS commit
    assert.equal(bash(r.dir, 'resolve-commit.sh', [pair[1].slice(0, 12)]).out.trim(), pair[1]);
    // a tree id is an object with a name but not a commit: never accepted as one
    const tree = r.g('rev-parse', `${shas[0]}^{tree}`);
    assert.equal(bash(r.dir, 'resolve-commit.sh', [tree]).code, 1, 'a tree is not a commit');
  } finally { r.done(); }
});
