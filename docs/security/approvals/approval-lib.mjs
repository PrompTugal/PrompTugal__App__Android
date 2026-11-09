// Reference implementation of the signed security approvals (docs/security/approvals.md). Node 18 or later, no dependency.
import { createHash, createPrivateKey, createPublicKey, sign as edSign, verify as edVerify } from 'node:crypto';
import { execFileSync } from 'node:child_process';

export const FORMAT = 'riskmandate-approval/1';
export const APPROVALS = 'board/security/approvals.jsonl';
export const KEYS = 'board/keys.json';
export const VERDICTS = ['PASS', 'HOLD', 'REVOKE'];
const SHA = /^[0-9a-f]{40}$/;
const HEX64 = /^[0-9a-f]{64}$/;
// A promotion that touches one of these needs a person: the automated promotion refuses (docs/security/approvals.md section 5).
export const TRUST_PATHS = [/^board\/keys\.json$/, /^\.github\//, /^scripts\/verify-approval/, /^scripts\/promote-select\.sh$/, /^scripts\/find-release-tag\.sh$/, /^scripts\/changed-build-paths\.sh$/, /^scripts\/changed-since-release\.sh$/, /^docs\/security\/approvals\//, /^CLAUDE\.md$/, /^board\/security\/approvals\.jsonl$/];

export const git = (repo, ...args) => execFileSync('git', ['-C', repo, ...args], { encoding: 'utf8', maxBuffer: 64 << 20 });
export const gitOrNull = (repo, ...args) => { try { return git(repo, ...args); } catch { return null; } };
export const sha256 = (b) => createHash('sha256').update(b).digest('hex');

/** The commits in base..head, oldest first, hashed; the count goes with it. */
export function rangeOf(repo, base, head) {
  const list = git(repo, 'rev-list', '--reverse', `${base}..${head}`).split('\n').filter(Boolean);
  return { commits: list.length, range: sha256(list.join('\n') + '\n') };
}

/** The exact bytes that are signed: a fixed order, a fixed first line, nothing re-serialised. */
export function message(a) {
  return Buffer.from([FORMAT, `head=${a.head}`, `base=${a.base}`, `range=${a.range}`, `commits=${a.commits}`, `verdict=${a.verdict}`,
    `review=${a.review}`, `review_sha256=${a.review_sha256}`, `ts=${a.ts}`, `expires=${a.expires}`, `key=${a.key}`, ''].join('\n'), 'utf8');
}

export function shape(a) {
  if (!a || a.v !== 1) return 'v must be 1';
  for (const k of ['head', 'base']) if (!SHA.test(a[k] ?? '')) return `${k} is a full sha`;
  if (!HEX64.test(a.range ?? '') || !HEX64.test(a.review_sha256 ?? '')) return 'range and review_sha256 are sha-256 hex';
  if (!Number.isInteger(a.commits) || a.commits < 0 || a.commits > 100000) return 'commits is a count';
  if (!VERDICTS.includes(a.verdict)) return 'verdict is PASS, HOLD or REVOKE';
  if (typeof a.review !== 'string' || !/^[A-Za-z0-9._\/-]{1,200}$/.test(a.review) || a.review.includes('..')) return 'review is a repository path';
  for (const k of ['ts', 'expires']) if (!/^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\dZ$/.test(a[k] ?? '')) return `${k} is a UTC time`;
  if (typeof a.key !== 'string' || !/^[a-z0-9.@-]{1,80}$/.test(a.key)) return 'key is an id';
  if (typeof a.sig !== 'string' || !/^[A-Za-z0-9+\/=]{80,100}$/.test(a.sig)) return 'sig is base64';
  return null;
}

export const fingerprint = (rawPub) => sha256(rawPub).slice(0, 16).replace(/(.{4})(?=.)/g, '$1 ');
const SPKI_PREFIX = Buffer.from('302a300506032b6570032100', 'hex');
export const publicKeyFromRaw = (raw) => createPublicKey({ key: Buffer.concat([SPKI_PREFIX, raw]), format: 'der', type: 'spki' });
export const rawPublic = (pub) => pub.export({ format: 'der', type: 'spki' }).subarray(SPKI_PREFIX.length);
export const signLine = (privatePem, a) => ({ ...a, sig: edSign(null, message(a), createPrivateKey(privatePem)).toString('base64') });

/** Reads the approvals file text into lines (only well-formed JSON objects; anything else is a failure). */
export function parseLines(text) {
  const out = [];
  for (const [i, l] of text.split('\n').entries()) {
    if (!l.trim()) continue;
    let o; try { o = JSON.parse(l); } catch { throw new Error(`approvals line ${i + 1} is not JSON`); }
    out.push(o);
  }
  return out;
}

// Paths a commit after the approved head may touch: the approval record itself and the reviews (a hash cannot contain its own approval).
export const TAIL_PATHS = [
  /^board\/security\/approvals\.jsonl$/, /^docs\/security\/reviews\/[A-Za-z0-9._-]+\.md$/, /^briefs\/[^/]+\/status\/security\.md$/,
  // Plain status text that no build, test or workflow reads (the board's status and sessions files, the status pages and debriefs): the
  // conductor commits these after nearly every merge, and they would otherwise stop every promotion. Never code, keys or workflows.
  /^board\/(status|sessions)\.json$/, /^board\/team\/[^/]+\/debriefs\/[A-Za-z0-9._-]+\.md$/, /^briefs\/[^/]+\/status\/[A-Za-z0-9._-]+\.md$/,
];
const isAncestor = (repo, a, b) => gitOrNull(repo, 'merge-base', '--is-ancestor', a, b) !== null;

/**
 * The check. `tip` is the dev commit being promoted. The line that approves it names an approved head H that is the tip
 * itself or an ancestor of it, with only the approval record and the reviews added between H and the tip (an approval
 * cannot be inside the commit it names). Keys and the approvals file as they are on `main` are the trust anchor: the
 * candidate may only append. Returns {ok, reason}. Never throws on bad data: a bad record is a failed check.
 */
export function check({ repo, main, tip, now = new Date(), allowTrustPathChange = false }) {
  try {
    if (!SHA.test(main) || !SHA.test(tip)) return fail('main and tip must be full shas');
    if (!isAncestor(repo, main, tip)) return fail('tip is not a descendant of main (only a fast-forward is promoted)');
    if (!allowTrustPathChange) {
      const changed = git(repo, 'diff', '--name-only', `${main}..${tip}`).split('\n').filter(Boolean)
        .filter((f) => TRUST_PATHS.some((r) => r.test(f)) && !TAIL_PATHS.some((r) => r.test(f)));
      if (changed.length) return fail(`tip changes trust paths (${changed.slice(0, 3).join(', ')}): a person promotes this one`);
    }
    const keysText = gitOrNull(repo, 'show', `${main}:${KEYS}`);
    if (keysText === null) return fail('no board/keys.json on main');
    const keys = JSON.parse(keysText).keys ?? [];
    const mainApprovals = gitOrNull(repo, 'show', `${main}:${APPROVALS}`) ?? '';
    const tipApprovals = gitOrNull(repo, 'show', `${tip}:${APPROVALS}`);
    if (tipApprovals === null) return fail('no approvals file on the tip');
    if (!tipApprovals.startsWith(mainApprovals)) return fail('approvals file is not an append to the one on main');
    const lines = parseLines(tipApprovals);
    const valid = [];
    lines.forEach((a, idx) => {
      if (shape(a)) return;
      const k = keys.find((x) => x.id === a.key && x.role === 'security' && x.alg === 'ed25519');
      if (!k) return;
      let ok = false;
      try { ok = edVerify(null, message(a), publicKeyFromRaw(Buffer.from(k.public_key, 'base64')), Buffer.from(a.sig, 'base64')); } catch { ok = false; }
      if (ok) valid.push({ a, idx, key: k });
    });
    // Lines about a head that is the tip or an ancestor of it and past main; the latest one decides.
    const about = valid.filter((v) => v.a.head === tip || (isAncestor(repo, v.a.head, tip) && isAncestor(repo, main, v.a.head)));
    if (!about.length) return fail('no valid signed line for this tip');
    const last = about[about.length - 1];
    const a = last.a;
    if (a.verdict !== 'PASS') return fail(`the latest line is ${a.verdict} for ${a.head.slice(0, 7)}`);
    if (last.key.status !== 'active') return fail('the signing key is not active');
    if (a.base !== main) return fail('the approval was made against another main');
    const { commits, range } = rangeOf(repo, main, a.head);
    if (a.range !== range || a.commits !== commits) return fail('the commits in main..approved head are not the ones that were reviewed');
    if (a.head !== tip) {
      const tail = git(repo, 'diff', '--name-only', `${a.head}..${tip}`).split('\n').filter(Boolean).filter((f) => !TAIL_PATHS.some((r) => r.test(f)));
      if (tail.length) return fail(`after the approved head the tip changes ${tail.slice(0, 3).join(', ')}, which is more than the approval record and reviews`);
    }
    const t = Date.parse(a.ts), e = Date.parse(a.expires);
    if (!(t <= now.getTime())) return fail('the approval is dated in the future');
    if (!(e > now.getTime()) || e - t > 7 * 864e5) return fail('the approval has expired (or lasts longer than seven days)');
    const revoked = valid.find((v) => v.idx > last.idx && v.a.verdict === 'REVOKE' && (v.a.head === tip || (isAncestor(repo, v.a.head, tip) && isAncestor(repo, main, v.a.head))));
    if (revoked) return fail(`a later REVOKE covers ${revoked.a.head.slice(0, 7)}`);
    return { ok: true, reason: `PASS by ${a.key} for ${a.head.slice(0, 7)}, ${commits} commits reviewed, review ${a.review}` };
  } catch (e) {
    return fail(`check error: ${e.message}`);
  }
  function fail(reason) { return { ok: false, reason }; }
}
