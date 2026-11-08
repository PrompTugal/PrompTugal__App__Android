#!/usr/bin/env node
// Trims the AMAPI discovery document to what the offline policy test needs.
//   node devices/trim-discovery.mjs <full discovery.json> > devices/schema/androidmanagement-v1.trimmed.json
// The full document comes from https://androidmanagement.googleapis.com/$discovery/rest?version=v1 (about 470 KB).
// Kept: the schemas reachable from the roots below (types, enums, $ref, items, additionalProperties, deprecated flags),
// and the methods amapi.mjs calls (HTTP method, path, parameter names and locations, request and response schema).
// Dropped: every description (prose), icons, batch paths and the rest of the API.
import fs from 'node:fs';

const ROOTS = ['Policy', 'EnrollmentToken', 'WebToken', 'Enterprise', 'SignupUrl', 'Device'];
const METHODS = {
  'signupUrls.create': (r) => r.signupUrls.methods.create,
  'enterprises.create': (r) => r.enterprises.methods.create,
  'enterprises.policies.patch': (r) => r.enterprises.resources.policies.methods.patch,
  'enterprises.enrollmentTokens.create': (r) => r.enterprises.resources.enrollmentTokens.methods.create,
  'enterprises.devices.delete': (r) => r.enterprises.resources.devices.methods.delete,
  'enterprises.enrollmentTokens.delete': (r) => r.enterprises.resources.enrollmentTokens.methods.delete,
  'enterprises.devices.list': (r) => r.enterprises.resources.devices.methods.list,
  'enterprises.webTokens.create': (r) => r.enterprises.resources.webTokens.methods.create,
};
const KEEP = ['type', 'format', 'enum', 'enumDeprecated', '$ref', 'items', 'additionalProperties', 'properties', 'deprecated', 'readOnly'];

function trimSchema(s) {
  const out = {};
  for (const k of KEEP) {
    if (s[k] === undefined) continue;
    if (k === 'properties') out.properties = Object.fromEntries(Object.entries(s.properties).map(([n, p]) => [n, trimSchema(p)]));
    else if (k === 'items' || k === 'additionalProperties') out[k] = trimSchema(s[k]);
    else out[k] = s[k];
  }
  return out;
}

function refs(s, acc) {
  if (!s || typeof s !== 'object') return;
  if (s.$ref) acc.add(s.$ref);
  for (const v of Object.values(s)) refs(v, acc);
}

export function trim(d) {
  const keep = new Set();
  const queue = [...ROOTS];
  while (queue.length) {
    const n = queue.pop();
    if (keep.has(n)) continue;
    keep.add(n);
    const found = new Set();
    refs(d.schemas[n], found);
    queue.push(...found);
  }
  const schemas = {};
  for (const n of [...keep].sort()) schemas[n] = trimSchema(d.schemas[n]);
  const methods = {};
  for (const [id, pick] of Object.entries(METHODS)) {
    const m = pick(d.resources);
    methods[id] = {
      httpMethod: m.httpMethod, path: m.path,
      parameters: Object.fromEntries(Object.entries(m.parameters || {}).map(([k, p]) => [k, { type: p.type, location: p.location, required: !!p.required }])),
      ...(m.request ? { request: m.request.$ref } : {}), response: m.response.$ref,
    };
  }
  return {
    _about: 'Trimmed copy of the Android Management API v1 discovery document, for the offline policy test (devices/test/policy-schema.test.mjs). Regenerate with devices/trim-discovery.mjs. Descriptions are dropped; read them in the full document.',
    source: 'https://androidmanagement.googleapis.com/$discovery/rest?version=v1',
    revision: d.revision, fetched: '2026-11-08', rootUrl: d.rootUrl,
    schemas, methods,
  };
}

if (process.argv[1] && import.meta.url === new URL(`file://${process.argv[1]}`).href) {
  const d = JSON.parse(fs.readFileSync(process.argv[2], 'utf8'));
  process.stdout.write(`${JSON.stringify(trim(d), null, 1)}\n`);
}
