#!/usr/bin/env node
// The `security-approval` check. Run from the workflow's checkout of main, not from the candidate:
//   node verify-approval.mjs --repo . --main <main sha> --tip <dev sha>
import { check } from './approval-lib.mjs';
const arg = (n) => { const i = process.argv.indexOf(`--${n}`); return i < 0 ? undefined : process.argv[i + 1]; };
const r = check({ repo: arg('repo') ?? '.', main: arg('main'), tip: arg('tip') });
console.log(JSON.stringify(r));
process.exit(r.ok ? 0 : 1);
