#!/usr/bin/env node
// Self-test with throwaway keys made in memory; no key is committed. Run: node selftest.mjs
import { generateKeyPairSync } from 'node:crypto';
import { mkdtempSync, writeFileSync, mkdirSync, readFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, dirname } from 'node:path';
import { execFileSync } from 'node:child_process';
import { check, git, rangeOf, sha256, signLine, rawPublic, APPROVALS, KEYS } from './approval-lib.mjs';

let failures = 0;
const expect = (name, got, want, why) => { const ok = got.ok === want && (!why || got.reason.includes(why)); console.log(`${ok ? 'ok  ' : 'FAIL'} ${name}${ok ? '' : ` -> ${JSON.stringify(got)}`}`); if (!ok) failures++; };
const pem = (k) => k.privateKey.export({ format: 'pem', type: 'pkcs8' });
const sec = generateKeyPairSync('ed25519'), other = generateKeyPairSync('ed25519');
const dir = mkdtempSync(join(tmpdir(), 'approval-test-'));
const g = (...a) => git(dir, ...a).trim();
const put = (p, t) => { mkdirSync(dirname(join(dir, p)), { recursive: true }); writeFileSync(join(dir, p), t); };
const commit = (m) => { g('add', '-A'); execFileSync('git', ['-C', dir, '-c', 'user.name=t', '-c', 'user.email=t@example.com', 'commit', '-q', '-m', m]); return g('rev-parse', 'HEAD'); };
const keysJson = (k, status = 'active') => JSON.stringify({ keys: [{ id: 'security.android', role: 'security', alg: 'ed25519', public_key: rawPublic(k.publicKey).toString('base64'), status }] });

g('init', '-q', '-b', 'main');
put(KEYS, keysJson(sec)); put(APPROVALS, ''); put('app.txt', '1');
const main = commit('main');
const fresh = (name) => { g('checkout', '-q', '-B', name, main); put('app.txt', name + 'a'); commit('a'); put('app.txt', name + 'b'); return commit('b'); };
const review = (h) => 'docs/security/reviews/2026-11-09-dev-' + h.slice(0, 7) + '.md';
const now = new Date('2026-11-09T15:00:00Z');
const line = (h, over = {}, k = sec) => {
  put(review(h), 'Verdict: PASS\n');
  const { commits, range } = rangeOf(dir, main, h);
  return signLine(pem(k), { v: 1, head: h, base: main, range, commits, verdict: 'PASS', review: review(h), review_sha256: sha256(readFileSync(join(dir, review(h)))), ts: '2026-11-09T14:00:00Z', expires: '2026-11-12T14:00:00Z', key: 'security.android', ...over });
};
const append = (lines) => { const cur = readFileSync(join(dir, APPROVALS), 'utf8'); put(APPROVALS, cur + lines.map((l) => JSON.stringify(l)).join('\n') + '\n'); return commit('approvals'); };
const run = (tip, extra = {}) => check({ repo: dir, main, tip, now, ...extra });

let h = fresh('t1'); let tip = append([line(h)]);
expect('a signed PASS for the approved head, the record committed on top, is promoted', run(tip), true);
h = fresh('t2'); tip = h;
expect('no record at all', run(tip), false, 'no valid signed line');
h = fresh('t3'); tip = append([line(h, {}, other)]);
expect('a line signed by a key that is not on main', run(tip), false, 'no valid signed line');
h = fresh('t4'); let l = line(h); l.verdict = 'PASS'; l.commits = l.commits + 1; tip = append([l]);
expect('a tampered field breaks the signature', run(tip), false, 'no valid signed line');
h = fresh('t5'); tip = append([line(h, { expires: '2026-11-09T14:30:00Z' })]);
expect('an expired approval', run(tip), false, 'expired');
h = fresh('t6'); tip = append([line(h, { expires: '2026-11-30T14:00:00Z' })]);
expect('an approval that lasts more than seven days', run(tip), false, 'expired');
h = fresh('t7'); tip = append([line(h, { ts: '2026-11-10T14:00:00Z', expires: '2026-11-11T14:00:00Z' })]);
expect('an approval dated in the future', run(tip), false, 'future');
h = fresh('t8'); tip = append([line(h, { verdict: 'HOLD' })]);
expect('a HOLD', run(tip), false, 'HOLD');
h = fresh('t9'); tip = append([line(h), line(h, { verdict: 'HOLD' })]);
expect('a later HOLD for the same head beats the PASS', run(tip), false, 'HOLD');
h = fresh('t10'); tip = append([line(h), line(h, { verdict: 'REVOKE' })]);
expect('a later REVOKE', run(tip), false);
h = fresh('t11'); const x = append([line(h)]); put('app.txt', 'sneaked'); tip = commit('code after the approval');
expect('code added after the approved head', run(tip), false, 'more than the approval record');
h = fresh('t12'); const l12 = line(h); g('checkout', '-q', '-B', 't12b', main); put('app.txt', 'a different history'); const h2 = commit('x'); tip = append([{ ...l12 }]);
expect('a line for a head that is not in this history', run(tip), false);
h = fresh('t13'); put(KEYS, keysJson(other)); const swap = commit('swap the key'); const sl = line(swap, {}, other); tip = append([sl]);
expect('a candidate that swaps board/keys.json cannot approve itself (trust paths and keys come from main)', run(tip), false);
h = fresh('t14'); put('.github/workflows/promote.yml', 'name: x\n'); const wf = commit('workflow'); tip = append([line(wf)]);
expect('a head that changes a workflow is refused for automatic promotion', run(tip), false, 'trust paths');
expect('... and goes through when a person promotes it (allowTrustPathChange)', run(tip, { allowTrustPathChange: true }), true);
// An approvals file on the candidate that is not an append to the one on main (a line removed or rewritten).
g('checkout', '-q', '-B', 'main2', main); const lm = line(main); put(APPROVALS, JSON.stringify(lm) + '\n'); const main2 = commit('main with one line');
g('checkout', '-q', '-B', 't15', main2); put('app.txt', 'c'); const c15 = commit('code'); put(APPROVALS, ''); const tip15 = commit('rewrite the approvals file');
expect('a candidate whose approvals file is not an append to the one on main', check({ repo: dir, main: main2, tip: tip15, now }), false, 'not an append');
console.log(failures ? `${failures} failing` : 'all ok'); process.exit(failures ? 1 : 0);
