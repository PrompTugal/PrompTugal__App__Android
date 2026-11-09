// Tests for select-promotion.mjs, changed-build-paths.sh and find-release-tag.sh in throwaway git repositories.
import test from 'node:test';
import assert from 'node:assert/strict';
import { generateKeyPairSync, sign } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { mkdtempSync, writeFileSync, mkdirSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import { select } from '../select-promotion.mjs';
import { signedText } from '../verify-approval.mjs';

const scripts = resolve(import.meta.dirname, '..');
function repo() {
  const dir = mkdtempSync(join(tmpdir(), 'flow-'));
  const g = (...a) => execFileSync('git', a, { cwd: dir, encoding: 'utf8' }).trim();
  g('init', '-q', '-b', 'dev'); g('config', 'user.email', 't@example.invalid'); g('config', 'user.name', 't'); g('config', 'commit.gpgsign', 'false');
  const commit = (file, msg) => { mkdirSync(join(dir, file, '..'), { recursive: true }); writeFileSync(join(dir, file), msg + Math.random()); g('add', '-A'); g('commit', '-q', '-m', msg); return g('rev-parse', 'HEAD'); };
  return { dir, g, commit, done: () => rmSync(dir, { recursive: true, force: true }) };
}
const bash = (dir, script, ...args) => { try { return { code: 0, out: execFileSync('bash', [join(scripts, script), ...args], { cwd: dir, encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] }) }; } catch (e) { return { code: e.status, out: String(e.stdout) }; } };

const kp = generateKeyPairSync('ed25519');
const keysJson = { keys: [{ id: 'sec1', role: 'security', alg: 'ed25519', public_key: kp.publicKey.export({ type: 'spki', format: 'der' }).toString('base64') }] };
const rec = (sha, verdict = 'PASS', signed_at = '2026-11-09T15:00:00Z') => {
  const base = { sha, verdict, review: 'docs/security/reviews/x.md', signed_at, key_id: 'sec1' };
  return JSON.stringify({ ...base, sig: sign(null, Buffer.from(signedText(base)), kp.privateKey).toString('base64') });
};

test('select: the newest approved dev commit that is ahead of main is promoted', () => {
  const r = repo(); try {
    const base = r.commit('README.md', 'base'); r.g('branch', 'main');
    const a = r.commit('app/a.txt', 'a'); const b = r.commit('app/b.txt', 'b'); const c = r.commit('app/c.txt', 'c');
    const text = rec(a) + '\n' + rec(b) + '\n';
    const s = select({ main: 'main', dev: 'dev', approvalsText: text, keysJson, cwd: r.dir });
    assert.equal(s.sha, b);       // b is approved and the newest approved; c is unapproved
    assert.equal(s.ahead, 2);
    assert.equal(select({ main: 'main', dev: 'dev', approvalsText: rec(c, 'HOLD'), keysJson, cwd: r.dir }).sha, null);
    assert.equal(select({ main: 'main', dev: 'dev', approvalsText: '', keysJson, cwd: r.dir }).sha, null);
    void base;
  } finally { r.done(); }
});

test('select: not a fast-forward, not on dev, already main, unknown commit, forged: never promoted', () => {
  const r = repo(); try {
    r.commit('README.md', 'base'); r.g('branch', 'main');
    r.g('checkout', '-q', 'main'); const m = r.commit('app/m.txt', 'main moves'); r.g('checkout', '-q', 'dev');
    const d = r.commit('app/d.txt', 'dev diverges');       // main is not an ancestor of d
    r.g('checkout', '-q', '-b', 'other', 'main~0'); const o = r.commit('app/o.txt', 'not on dev'); r.g('checkout', '-q', 'dev');
    const forged = (() => { const k2 = generateKeyPairSync('ed25519'); const base = { sha: d, verdict: 'PASS', review: 'x.md', signed_at: '2026-11-09T15:00:00Z', key_id: 'sec1' }; return JSON.stringify({ ...base, sig: sign(null, Buffer.from(signedText(base)), k2.privateKey).toString('base64') }); })();
    const text = [rec(d), rec(o), rec(m), rec('c'.repeat(40))].join('\n');
    assert.equal(select({ main: 'main', dev: 'dev', approvalsText: text, keysJson, cwd: r.dir }).sha, null);
    assert.equal(select({ main: 'main', dev: 'dev', approvalsText: forged, keysJson, cwd: r.dir }).sha, null);
  } finally { r.done(); }
});

test('changed-build-paths: docs-only is no, app and workflow and gradle files are yes', () => {
  const r = repo(); try {
    const base = r.commit('README.md', 'base');
    const docs = r.commit('docs/x.md', 'docs'); const board = r.commit('board/status.json', 'board'); const dev = r.commit('devices/amapi.mjs', 'devices'); const web = r.commit('website/index.html', 'site');
    assert.equal(bash(r.dir, 'changed-build-paths.sh', base, web).code, 1);
    for (const [file, name] of [['app/src/Main.kt', 'app'], ['.github/workflows/android.yml', 'workflow'], ['gradle/libs.toml', 'gradle'], ['build.gradle.kts', 'root kts'], ['app/build.gradle.kts', 'app kts'], ['gradle.properties', 'props'], ['gradlew', 'wrapper'], ['scripts/x.sh', 'scripts']]) {
      const before = r.g('rev-parse', 'HEAD'); r.commit(file, name);
      const res = bash(r.dir, 'changed-build-paths.sh', before, 'HEAD');
      assert.equal(res.code, 0, name); assert.match(res.out, new RegExp(file.replace(/[.]/g, '\\.')));
    }
    void docs; void board; void dev;
    assert.equal(bash(r.dir, 'changed-build-paths.sh', '0'.repeat(40), 'HEAD').code, 0, 'unknown base: assume changed');
    assert.equal(bash(r.dir, 'changed-build-paths.sh', 'f'.repeat(40), 'HEAD').code, 0, 'missing commit: assume changed');
  } finally { r.done(); }
});

test('find-release-tag: the newest tag with no build change since; none when the app changed after every tag', () => {
  const r = repo(); try {
    r.commit('app/a.txt', 'app 1'); r.g('tag', 'v0.1.10');
    r.commit('app/b.txt', 'app 2'); r.g('tag', 'v0.1.11');
    const docs = r.commit('docs/x.md', 'docs only');
    assert.equal(bash(r.dir, 'find-release-tag.sh', docs).out.trim(), 'v0.1.11');
    const app3 = r.commit('app/c.txt', 'app 3 (no release)');
    assert.equal(bash(r.dir, 'find-release-tag.sh', app3).code, 1, 'built code with no release must not be uploaded');
    r.g('tag', 'v0.1.12');
    assert.equal(bash(r.dir, 'find-release-tag.sh', app3).out.trim(), 'v0.1.12');
    // v0.1.9 is higher than v0.1.10 only if sorted as text: the sort must be by version
    r.commit('docs/y.md', 'docs 2'); r.g('tag', 'v0.1.9');
    const head = r.g('rev-parse', 'HEAD');
    assert.equal(bash(r.dir, 'find-release-tag.sh', head).out.trim(), 'v0.1.12');
  } finally { r.done(); }
});
