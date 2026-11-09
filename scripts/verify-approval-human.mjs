#!/usr/bin/env node
// The same check as verify-approval.mjs (security.android's, copied unchanged), for a PROMOTION TRIGGERED BY A
// PERSON only: it passes allowTrustPathChange, so a commit that changes a trust path (workflows, keys, the
// verifier, CLAUDE.md) can be uploaded once a person decided (docs/security/approvals.md section 5). Never use
// it from an automatic trigger. Usage: node scripts/verify-approval-human.mjs --repo . --main <sha> --tip <sha>
import { check } from './verify-approval-lib.mjs';
const arg = (n) => { const i = process.argv.indexOf(`--${n}`); return i < 0 ? undefined : process.argv[i + 1]; };
const r = check({ repo: arg('repo') ?? '.', main: arg('main'), tip: arg('tip'), allowTrustPathChange: true });
console.log(JSON.stringify(r));
process.exit(r.ok ? 0 : 1);
