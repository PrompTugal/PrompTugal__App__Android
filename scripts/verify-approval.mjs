#!/usr/bin/env node
// Verifies the security role's signed approvals (board/security/approvals.jsonl).
// No dependency: Node's built-in crypto (Ed25519). PROPOSED format, to be confirmed or replaced by
// security.android (docs/ci.md, "Release flow"); the checks below fail closed.
//
// Usage: node scripts/verify-approval.mjs --sha <40 hex> --approvals <file> --keys <file> [--role security]
//   exit 0: the newest valid record for that SHA is a PASS signed by an active key of that role
//   exit 1: not approved (the reason is printed); exit 2: bad usage or unreadable input
//
// A record is one JSON object per line:
//   {"sha":"<40 hex>","verdict":"PASS"|"HOLD","review":"<repo path of the review>","signed_at":"YYYY-MM-DDTHH:MM:SSZ",
//    "key_id":"<id in keys.json>","sig":"<base64 Ed25519 signature>"}
// The signature is over the UTF-8 text  MESSAGE_PREFIX \n sha \n verdict \n review \n signed_at \n key_id \n
// keys.json: {"keys":[{"id":"...","role":"security","alg":"ed25519","public_key":"<base64 SPKI DER or PEM>","revoked":false}]}
//
// Rules: records with a bad shape, an unknown or revoked key, a key of another role or a bad signature are
// ignored (and counted). Among the valid records for one SHA the NEWEST signed_at wins, and a HOLD beats a
// PASS signed at the same time: re-appending an old PASS after a HOLD cannot undo the HOLD.
import { createPublicKey, verify } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { pathToFileURL } from 'node:url';

export const MESSAGE_PREFIX = 'promptugal-android-approval-v1';
const SHA = /^[0-9a-f]{40}$/;
const WHEN = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z$/;
const KEYID = /^[A-Za-z0-9._-]{1,64}$/;
const REVIEW = /^[A-Za-z0-9._\/-]{1,200}$/;

export function signedText({ sha, verdict, review, signed_at, key_id }) {
  return [MESSAGE_PREFIX, sha, verdict, review, signed_at, key_id].join('\n') + '\n';
}

function publicKey(text) {
  const t = String(text).trim();
  return t.includes('BEGIN PUBLIC KEY')
    ? createPublicKey(t)
    : createPublicKey({ key: Buffer.from(t, 'base64'), format: 'der', type: 'spki' });
}

/** Returns { valid: [{sha, verdict, signed_at, key_id, review}], ignored: [reason...] } for all records. */
export function validRecords({ approvalsText, keysJson, role = 'security' }) {
  const ignored = [];
  const keys = new Map();
  for (const k of (keysJson && Array.isArray(keysJson.keys)) ? keysJson.keys : []) {
    if (!k || typeof k.id !== 'string' || !KEYID.test(k.id)) continue;
    if (k.role !== role || k.alg !== 'ed25519' || k.revoked === true) continue;
    try {
      const pk = publicKey(k.public_key);
      if (pk.asymmetricKeyType === 'ed25519') keys.set(k.id, pk);
    } catch { /* an unreadable key is simply not trusted */ }
  }
  const valid = [];
  String(approvalsText).split('\n').forEach((line, i) => {
    if (!line.trim()) return;
    let r;
    try { r = JSON.parse(line); } catch { ignored.push(`line ${i + 1}: not JSON`); return; }
    const ok = r && typeof r === 'object'
      && SHA.test(r.sha ?? '') && (r.verdict === 'PASS' || r.verdict === 'HOLD')
      && REVIEW.test(r.review ?? '') && WHEN.test(r.signed_at ?? '') && KEYID.test(r.key_id ?? '')
      && typeof r.sig === 'string' && /^[A-Za-z0-9+\/]+={0,2}$/.test(r.sig);
    if (!ok) { ignored.push(`line ${i + 1}: bad shape`); return; }
    const pk = keys.get(r.key_id);
    if (!pk) { ignored.push(`line ${i + 1}: key ${r.key_id} is not an active ${role} key`); return; }
    const sig = Buffer.from(r.sig, 'base64');
    if (sig.length !== 64 || !verify(null, Buffer.from(signedText(r)), pk, sig)) {
      ignored.push(`line ${i + 1}: bad signature`); return;
    }
    valid.push({ sha: r.sha, verdict: r.verdict, review: r.review, signed_at: r.signed_at, key_id: r.key_id });
  });
  return { valid, ignored };
}

/** The deciding record per SHA: newest signed_at, HOLD beats PASS on a tie. */
export function decisions(args) {
  const { valid, ignored } = validRecords(args);
  const best = new Map();
  for (const r of valid) {
    const cur = best.get(r.sha);
    if (!cur || r.signed_at > cur.signed_at || (r.signed_at === cur.signed_at && r.verdict === 'HOLD')) best.set(r.sha, r);
  }
  return { best, ignored };
}

export function verifyApproval({ sha, approvalsText, keysJson, role = 'security' }) {
  if (!SHA.test(sha ?? '')) return { ok: false, reason: 'the SHA must be 40 lower-case hex digits' };
  const { best, ignored } = decisions({ approvalsText, keysJson, role });
  const d = best.get(sha);
  if (!d) return { ok: false, reason: `no valid ${role} record for ${sha} (${ignored.length} record(s) ignored${ignored.length ? ': ' + ignored.slice(0, 3).join('; ') : ''})` };
  if (d.verdict !== 'PASS') return { ok: false, reason: `the newest ${role} record for ${sha} is ${d.verdict} (${d.review}, ${d.signed_at})` };
  return { ok: true, reason: `${sha} has a valid PASS signed by ${d.key_id} at ${d.signed_at} (${d.review})`, record: d };
}

function main(argv) {
  const a = {};
  for (let i = 0; i < argv.length; i += 2) a[argv[i].replace(/^--/, '')] = argv[i + 1];
  if (!a.sha || !a.approvals || !a.keys) { console.error('usage: verify-approval.mjs --sha <sha> --approvals <file> --keys <file> [--role security]'); return 2; }
  let approvalsText, keysJson;
  try { approvalsText = readFileSync(a.approvals, 'utf8'); keysJson = JSON.parse(readFileSync(a.keys, 'utf8')); }
  catch (e) { console.error('cannot read the input: ' + e.message); return 2; }
  const r = verifyApproval({ sha: a.sha, approvalsText, keysJson, role: a.role || 'security' });
  (r.ok ? console.log : console.error)((r.ok ? 'APPROVED: ' : 'NOT APPROVED: ') + r.reason);
  return r.ok ? 0 : 1;
}
if (import.meta.url === pathToFileURL(process.argv[1] ?? '').href) process.exit(main(process.argv.slice(2)));
