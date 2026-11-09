// Tests for scripts/verify-approval.mjs. Keys are generated here at run time: no key is in the repository.
// Run: node --test scripts/test/
import test from 'node:test';
import assert from 'node:assert/strict';
import { generateKeyPairSync, sign } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { mkdtempSync, writeFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { verifyApproval, validRecords, signedText } from '../verify-approval.mjs';

const SHA = 'a'.repeat(40), OTHER = 'b'.repeat(40);
const pair = () => generateKeyPairSync('ed25519');
const spki = (k) => k.publicKey.export({ type: 'spki', format: 'der' }).toString('base64');
const entry = (id, k, extra = {}) => ({ id, role: 'security', alg: 'ed25519', public_key: spki(k), ...extra });
function record(k, { sha = SHA, verdict = 'PASS', review = 'docs/security/reviews/x.md', signed_at = '2026-11-09T15:00:00Z', key_id = 'sec1' } = {}, tamper = {}) {
  const base = { sha, verdict, review, signed_at, key_id };
  const sig = sign(null, Buffer.from(signedText(base)), k.privateKey).toString('base64');
  return JSON.stringify({ ...base, sig, ...tamper });
}
const lines = (...l) => l.join('\n') + '\n';

test('a valid PASS signed by an active security key is approved', () => {
  const k = pair();
  const r = verifyApproval({ sha: SHA, approvalsText: lines(record(k)), keysJson: { keys: [entry('sec1', k)] } });
  assert.equal(r.ok, true, r.reason);
});

test('works with a PEM public key too', () => {
  const k = pair();
  const pem = k.publicKey.export({ type: 'spki', format: 'pem' });
  const r = verifyApproval({ sha: SHA, approvalsText: lines(record(k)), keysJson: { keys: [{ ...entry('sec1', k), public_key: pem }] } });
  assert.equal(r.ok, true, r.reason);
});

test('no record for the SHA, or a record for another SHA, is not approved', () => {
  const k = pair(); const keys = { keys: [entry('sec1', k)] };
  assert.equal(verifyApproval({ sha: SHA, approvalsText: '', keysJson: keys }).ok, false);
  assert.equal(verifyApproval({ sha: SHA, approvalsText: lines(record(k, { sha: OTHER })), keysJson: keys }).ok, false);
});

test('a record moved to another SHA (replay) fails the signature', () => {
  const k = pair(); const keys = { keys: [entry('sec1', k)] };
  const moved = record(k, { sha: OTHER }, { sha: SHA });
  assert.equal(verifyApproval({ sha: SHA, approvalsText: lines(moved), keysJson: keys }).ok, false);
});

test('a tampered verdict, review or time fails the signature', () => {
  const k = pair(); const keys = { keys: [entry('sec1', k)] };
  const asApproved = (text) => verifyApproval({ sha: SHA, approvalsText: lines(text), keysJson: keys }).ok;
  assert.equal(asApproved(record(k)), true, 'control: untampered');
  assert.equal(asApproved(record(k, { verdict: 'HOLD' }, { verdict: 'PASS' })), false, 'HOLD edited to PASS');
  assert.equal(asApproved(record(k, {}, { review: 'docs/other.md' })), false, 'review path edited');
  assert.equal(asApproved(record(k, {}, { signed_at: '2030-01-01T00:00:00Z' })), false, 'time edited');
  assert.equal(asApproved(record(k, {}, { key_id: 'sec2' })), false, 'key id edited');
});

test('a signature by a key that is not in keys.json, or by another role, or revoked, is ignored', () => {
  const k = pair(), stranger = pair();
  const rec = lines(record(stranger));
  assert.equal(verifyApproval({ sha: SHA, approvalsText: rec, keysJson: { keys: [entry('sec1', k)] } }).ok, false);
  assert.equal(verifyApproval({ sha: SHA, approvalsText: lines(record(k)), keysJson: { keys: [entry('sec1', k, { role: 'app' })] } }).ok, false, 'wrong role');
  assert.equal(verifyApproval({ sha: SHA, approvalsText: lines(record(k)), keysJson: { keys: [entry('sec1', k, { revoked: true })] } }).ok, false, 'revoked');
  assert.equal(verifyApproval({ sha: SHA, approvalsText: lines(record(k)), keysJson: { keys: [] } }).ok, false, 'no keys (the repository today)');
  assert.equal(verifyApproval({ sha: SHA, approvalsText: lines(record(k)), keysJson: {} }).ok, false, 'no keys array');
});

test('a key of the right id but a different key pair fails', () => {
  const k = pair(), imposter = pair();
  assert.equal(verifyApproval({ sha: SHA, approvalsText: lines(record(imposter)), keysJson: { keys: [entry('sec1', k)] } }).ok, false);
});

test('an RSA or unreadable public key is never trusted', () => {
  const k = pair();
  assert.equal(verifyApproval({ sha: SHA, approvalsText: lines(record(k)), keysJson: { keys: [{ ...entry('sec1', k), public_key: 'not a key' }] } }).ok, false);
  assert.equal(verifyApproval({ sha: SHA, approvalsText: lines(record(k)), keysJson: { keys: [{ ...entry('sec1', k), alg: 'rsa' }] } }).ok, false);
});

test('the newest valid record wins: a later HOLD beats an earlier PASS, a later PASS beats an earlier HOLD', () => {
  const k = pair(); const keys = { keys: [entry('sec1', k)] };
  const pass = record(k, { signed_at: '2026-11-09T15:00:00Z' });
  const hold = record(k, { verdict: 'HOLD', signed_at: '2026-11-09T16:00:00Z' });
  const pass2 = record(k, { signed_at: '2026-11-09T17:00:00Z' });
  assert.equal(verifyApproval({ sha: SHA, approvalsText: lines(pass, hold), keysJson: keys }).ok, false);
  assert.equal(verifyApproval({ sha: SHA, approvalsText: lines(pass, hold, pass2), keysJson: keys }).ok, true);
});

test('re-appending an old PASS after a HOLD cannot undo the HOLD (position does not matter, signed time does)', () => {
  const k = pair(); const keys = { keys: [entry('sec1', k)] };
  const pass = record(k, { signed_at: '2026-11-09T15:00:00Z' });
  const hold = record(k, { verdict: 'HOLD', signed_at: '2026-11-09T16:00:00Z' });
  assert.equal(verifyApproval({ sha: SHA, approvalsText: lines(pass, hold, pass), keysJson: keys }).ok, false);
});

test('on the same signed time a HOLD beats a PASS', () => {
  const k = pair(); const keys = { keys: [entry('sec1', k)] };
  const t = '2026-11-09T15:00:00Z';
  assert.equal(verifyApproval({ sha: SHA, approvalsText: lines(record(k, { signed_at: t }), record(k, { verdict: 'HOLD', signed_at: t })), keysJson: keys }).ok, false);
});

test('garbage lines, bad shapes and bad SHAs are ignored without hiding a valid record', () => {
  const k = pair(); const keys = { keys: [entry('sec1', k)] };
  const text = lines('not json', '{}', '[]', '"x"', JSON.stringify({ sha: 'short', verdict: 'PASS' }), record(k));
  assert.equal(verifyApproval({ sha: SHA, approvalsText: text, keysJson: keys }).ok, true);
  const v = validRecords({ approvalsText: text, keysJson: keys });
  assert.equal(v.valid.length, 1); assert.equal(v.ignored.length, 5);
  assert.equal(verifyApproval({ sha: 'HEAD', approvalsText: text, keysJson: keys }).ok, false, 'a ref name is not a SHA');
  assert.equal(verifyApproval({ sha: SHA.toUpperCase(), approvalsText: text, keysJson: keys }).ok, false, 'upper case is not accepted');
});

test('a verdict other than PASS or HOLD is ignored', () => {
  const k = pair(); const keys = { keys: [entry('sec1', k)] };
  assert.equal(verifyApproval({ sha: SHA, approvalsText: lines(record(k, { verdict: 'MAYBE' })), keysJson: keys }).ok, false);
});

test('the command line: exit 0 approved, 1 not approved, 2 bad input', () => {
  const k = pair(); const dir = mkdtempSync(join(tmpdir(), 'appr-'));
  try {
    writeFileSync(join(dir, 'keys.json'), JSON.stringify({ keys: [entry('sec1', k)] }));
    writeFileSync(join(dir, 'a.jsonl'), lines(record(k)));
    const run = (sha, approvals = 'a.jsonl') => {
      try { execFileSync('node', ['scripts/verify-approval.mjs', '--sha', sha, '--approvals', join(dir, approvals), '--keys', join(dir, 'keys.json')], { stdio: 'pipe' }); return 0; }
      catch (e) { return e.status; }
    };
    assert.equal(run(SHA), 0); assert.equal(run(OTHER), 1); assert.equal(run(SHA, 'missing.jsonl'), 2);
  } finally { rmSync(dir, { recursive: true, force: true }); }
});
