#!/usr/bin/env node
// Which dev commit may be promoted to main: the newest SHA with a valid security PASS
// (scripts/verify-approval.mjs) that is ahead of main (main is its ancestor) and on dev (an ancestor of
// the dev head). Prints the SHA on stdout, or nothing; the reason goes to stderr.
// Usage: node scripts/select-promotion.mjs --main <ref> --dev <ref> --approvals <file> --keys <file>
// Run it from main's checkout (promote.yml does), so dev cannot change the rules that gate promotion.
import { execFileSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { pathToFileURL } from 'node:url';
import { decisions } from './verify-approval.mjs';

export function select({ main, dev, approvalsText, keysJson, cwd = process.cwd() }) {
  const git = (...args) => execFileSync('git', args, { encoding: 'utf8', cwd }).trim();
  const isAncestor = (a, b) => { try { execFileSync('git', ['merge-base', '--is-ancestor', a, b], { stdio: 'ignore', cwd }); return true; } catch { return false; } };
  const mainSha = git('rev-parse', main), devSha = git('rev-parse', dev);
  const { best, ignored } = decisions({ approvalsText, keysJson });
  let pick = null, ahead = 0;
  const why = [];
  for (const [sha, d] of best) {
    if (d.verdict !== 'PASS') { why.push(`${sha.slice(0, 7)}: ${d.verdict}`); continue; }
    if (sha === mainSha) { why.push(`${sha.slice(0, 7)}: already main`); continue; }
    let exists = true; try { git('cat-file', '-e', sha + '^{commit}'); } catch { exists = false; }
    if (!exists) { why.push(`${sha.slice(0, 7)}: not in this repository`); continue; }
    if (!isAncestor(mainSha, sha)) { why.push(`${sha.slice(0, 7)}: main is not an ancestor (not a fast-forward)`); continue; }
    if (!isAncestor(sha, devSha)) { why.push(`${sha.slice(0, 7)}: not on dev`); continue; }
    const n = Number(git('rev-list', '--count', `${mainSha}..${sha}`));
    if (n > ahead) { pick = sha; ahead = n; }
  }
  return { sha: pick, ahead, why, ignored };
}

function main(argv) {
  const a = {};
  for (let i = 0; i < argv.length; i += 2) a[argv[i].replace(/^--/, '')] = argv[i + 1];
  if (!a.main || !a.dev || !a.approvals || !a.keys) { console.error('usage: select-promotion.mjs --main <ref> --dev <ref> --approvals <file> --keys <file>'); return 2; }
  let r;
  try { r = select({ main: a.main, dev: a.dev, approvalsText: readFileSync(a.approvals, 'utf8'), keysJson: JSON.parse(readFileSync(a.keys, 'utf8')) }); }
  catch (e) { console.error('cannot select: ' + e.message); return 2; }
  if (r.sha) { console.error(`promotable: ${r.sha} (${r.ahead} commit(s) ahead of main)`); console.log(r.sha); }
  else console.error('nothing promotable' + (r.why.length ? ': ' + r.why.join('; ') : '') + (r.ignored.length ? ` (${r.ignored.length} record(s) ignored)` : ''));
  return 0;
}
if (import.meta.url === pathToFileURL(process.argv[1] ?? '').href) process.exit(main(process.argv.slice(2)));
