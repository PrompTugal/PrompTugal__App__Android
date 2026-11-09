#!/usr/bin/env node
// security.android's signer. The private key lives in one file outside the repository (mode 0600) and is never printed.
//   node sign-approval.mjs --init
//   node sign-approval.mjs --sign --repo . --head <sha> --base <main sha> --verdict PASS|HOLD|REVOKE --review docs/security/reviews/<file>.md [--days 3]
import { generateKeyPairSync, createPrivateKey, createPublicKey } from 'node:crypto';
import { readFileSync, writeFileSync, existsSync, mkdirSync, appendFileSync, chmodSync } from 'node:fs';
import { homedir } from 'node:os';
import { dirname, join } from 'node:path';
import { APPROVALS, VERDICTS, rangeOf, git, sha256, signLine, shape, publicKeyFromRaw, rawPublic, fingerprint } from './approval-lib.mjs';

const arg = (n, d) => { const i = process.argv.indexOf(`--${n}`); return i < 0 ? d : process.argv[i + 1]; };
const has = (n) => process.argv.includes(`--${n}`);
const KEYFILE = process.env.RM_APPROVAL_KEY || join(homedir(), '.riskmandate', 'security-approval.ed25519');
const ID = process.env.RM_APPROVAL_ID || 'security.android';
const utc = (d) => d.toISOString().replace(/\.\d{3}Z$/, 'Z');

if (has('init')) {
  if (existsSync(KEYFILE)) { console.error('a key already exists; rotation goes through main (docs/security/approvals.md section 6)'); process.exit(2); }
  const { privateKey, publicKey } = generateKeyPairSync('ed25519');
  mkdirSync(dirname(KEYFILE), { recursive: true, mode: 0o700 });
  writeFileSync(KEYFILE, privateKey.export({ format: 'pem', type: 'pkcs8' }), { mode: 0o600 });
  chmodSync(KEYFILE, 0o600);
  const raw = rawPublic(publicKey);
  console.log(JSON.stringify({ id: ID, role: 'security', alg: 'ed25519', public_key: raw.toString('base64'), fingerprint: fingerprint(raw), status: 'active' }, null, 1));
  process.exit(0);
}
if (!has('sign')) { console.error('use --init or --sign'); process.exit(2); }
const repo = arg('repo', '.'), head = arg('head'), base = arg('base'), verdict = arg('verdict'), review = arg('review');
if (!VERDICTS.includes(verdict)) { console.error('verdict is PASS, HOLD or REVOKE'); process.exit(2); }
const resolve = (r) => git(repo, 'rev-parse', '--verify', `${r}^{commit}`).trim();
const H = resolve(head), B = resolve(base);
// The signer refuses what the file does not say: the review must exist and carry the same verdict and name this head.
const text = readFileSync(join(repo, review));
const first = text.toString('utf8').split('\n').find((l) => l.startsWith('Verdict:'));
if (first?.trim() !== `Verdict: ${verdict}` && !(verdict === 'REVOKE' && first?.trim() === 'Verdict: HOLD')) { console.error(`the review's Verdict line is "${first}", not ${verdict}`); process.exit(3); }
if (!review.includes(H.slice(0, 7))) { console.error(`the review file name must contain the head's short sha ${H.slice(0, 7)}`); process.exit(3); }
const { commits, range } = rangeOf(repo, B, H);
const now = new Date();
const days = Math.min(Number(arg('days', '3')), 7);
const line = signLine(readFileSync(KEYFILE, 'utf8'), { v: 1, head: H, base: B, range, commits, verdict, review, review_sha256: sha256(text), ts: utc(now), expires: utc(new Date(now.getTime() + days * 864e5)), key: ID });
const bad = shape(line); if (bad) { console.error(bad); process.exit(3); }
appendFileSync(join(repo, APPROVALS), JSON.stringify(line) + '\n');
console.log(`signed ${verdict} for ${H.slice(0, 7)} (${commits} commits since ${B.slice(0, 7)})`);
