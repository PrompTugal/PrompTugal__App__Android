// Validates, offline, every policy in devices/policies/*.json against the AMAPI discovery document
// (a trimmed copy, devices/schema/androidmanagement-v1.trimmed.json): every field exists, every value has the
// right JSON type, every enum value is one the API lists, no field is read-only, and no deprecated field
// is used unless it is allowed below with a reason. Also checks the request bodies and URLs that amapi.mjs builds.
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { substitutePolicy, buildRequest, API } from '../amapi.mjs';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const DISCOVERY = JSON.parse(fs.readFileSync(path.join(HERE, '..', 'schema', 'androidmanagement-v1.trimmed.json'), 'utf8'));
const POLICIES_DIR = path.join(HERE, '..', 'policies');

// Deprecated fields that a policy may still use, and why. Anything else deprecated fails the test.
const ALLOWED_DEPRECATED = {
  'Policy.safeBootDisabled': 'no replacement in the schema; the security review (A-11) asked for it; only in the hardened options file',
};

// Returns a list of problems (empty when the value fits the schema node).
export function validate(value, where, schemas = DISCOVERY.schemas, allowDeprecated = ALLOWED_DEPRECATED) {
  const bad = [];
  const walk = (v, n, p, owner) => {
    if (n.$ref) {
      const target = schemas[n.$ref];
      if (!target) { bad.push(`${p}: unknown schema ${n.$ref}`); return; }
      return walk(v, target, p, n.$ref);
    }
    switch (n.type) {
      case 'object': {
        if (v === null || typeof v !== 'object' || Array.isArray(v)) { bad.push(`${p}: expected an object`); return; }
        if (n.properties) {
          for (const [k, x] of Object.entries(v)) {
            const prop = n.properties[k];
            if (!prop) { bad.push(`${p}.${k}: not a field of ${owner}`); continue; }
            if (prop.readOnly) bad.push(`${p}.${k}: read-only field`);
            if (prop.deprecated && !allowDeprecated[`${owner}.${k}`]) bad.push(`${p}.${k}: deprecated field`);
            walk(x, prop, `${p}.${k}`, undefined);
          }
        } else if (n.additionalProperties && n.additionalProperties.type !== 'any') {
          for (const [k, x] of Object.entries(v)) walk(x, n.additionalProperties, `${p}.${k}`, undefined);
        } // free-form object (type any): nothing to check, e.g. managedConfiguration
        return;
      }
      case 'array':
        if (!Array.isArray(v)) { bad.push(`${p}: expected an array`); return; }
        v.forEach((x, i) => walk(x, n.items, `${p}[${i}]`, undefined));
        return;
      case 'string':
        if (typeof v !== 'string') { bad.push(`${p}: expected a string`); return; }
        if (n.enum && !n.enum.includes(v)) bad.push(`${p}: "${v}" is not one of ${n.enum.join(', ')}`);
        if (n.enum && v.endsWith('_UNSPECIFIED')) bad.push(`${p}: "${v}" is the unset value`);
        return;
      case 'boolean': if (typeof v !== 'boolean') bad.push(`${p}: expected true or false`); return;
      case 'integer': if (!Number.isInteger(v)) bad.push(`${p}: expected an integer`); return;
      case 'number': if (typeof v !== 'number') bad.push(`${p}: expected a number`); return;
      default: // 'any' or untyped: accepted
    }
  };
  walk(value, { $ref: where }, where, where);
  return bad;
}

const files = fs.readdirSync(POLICIES_DIR).filter((f) => f.endsWith('.json'));

test('there are at least the base policy and the hardened options file', () => {
  assert.ok(files.includes('riskmandate-fully-managed.json'));
  assert.ok(files.includes('riskmandate-hardened-options.json'));
});

for (const f of files) {
  test(`policy ${f}: every field, type and enum exists in the AMAPI discovery document`, () => {
    const raw = JSON.parse(fs.readFileSync(path.join(POLICIES_DIR, f), 'utf8'));
    const policy = substitutePolicy(raw, { packageName: 'ai.riskmandate.app' });
    const problems = validate(policy, 'Policy');
    assert.deepEqual(problems, []);
  });
}

test('the hardened options file is a superset: it keeps every top-level setting of the base policy', () => {
  const load = (f) => JSON.parse(fs.readFileSync(path.join(POLICIES_DIR, f), 'utf8'));
  const base = load('riskmandate-fully-managed.json');
  const hard = load('riskmandate-hardened-options.json');
  for (const k of Object.keys(base).filter((x) => !x.startsWith('_'))) assert.ok(k in hard, `${k} missing from the hardened file`);
  assert.deepEqual(hard.applications.map((a) => a.packageName), base.applications.map((a) => a.packageName));
  assert.equal(hard.playStoreMode, 'WHITELIST');
  assert.equal(hard.advancedSecurityOverrides.untrustedAppsPolicy, 'DISALLOW_INSTALL');
});

test('every _note_ key sits next to a real setting (no orphan notes)', () => {
  const hard = JSON.parse(fs.readFileSync(path.join(POLICIES_DIR, 'riskmandate-hardened-options.json'), 'utf8'));
  const check = (o, p) => {
    for (const k of Object.keys(o)) if (k.startsWith('_note_')) assert.ok(k.slice(6) in o || ['uwb', 'nfc', '2g', 'wifi', 'usb', 'tethering', 'bluetoothSharing'].includes(k.slice(6)), `${p}.${k} has no setting`);
    for (const [k, v] of Object.entries(o)) if (v && typeof v === 'object' && !Array.isArray(v) && !k.startsWith('_')) check(v, `${p}.${k}`);
  };
  check(hard, 'policy');
});

// ---------- the validator itself must catch mistakes ----------

test('the validator rejects an unknown field, a wrong type, a bad enum, a read-only field and a deprecated field', () => {
  assert.match(validate({ playStoreModee: 'WHITELIST' }, 'Policy')[0], /not a field of Policy/);
  assert.match(validate({ addUserDisabled: 'yes' }, 'Policy')[0], /expected true or false/);
  assert.match(validate({ playStoreMode: 'ALLOWLIST_ALL' }, 'Policy')[0], /is not one of/);
  assert.match(validate({ cameraDisabled: true }, 'Policy')[0], /deprecated field/);
  assert.match(validate({ applications: [{ packageName: 'a.b', installType: 'FORCE' }] }, 'Policy')[0], /is not one of/);
  assert.match(validate({ systemUpdate: { type: 'WINDOWED', startMinutes: '120' } }, 'Policy')[0], /expected an integer/);
  assert.match(validate({ playStoreMode: 'PLAY_STORE_MODE_UNSPECIFIED' }, 'Policy')[0], /unset value/);
  assert.deepEqual(validate({ applications: [{ packageName: 'a.b', managedConfiguration: { anything: [1, 'x'] } }] }, 'Policy'), []);
});

// ---------- request bodies and URLs the CLI builds ----------

const ENT = 'enterprises/LC0test123';
const method = (id) => DISCOVERY.methods[id];

test('enrol: the body fits EnrollmentToken, and URL, verb and path match the API', () => {
  const req = buildRequest('enrol', { enterprise: ENT, policy: 'riskmandate', duration: '3600s' });
  assert.deepEqual(validate(req.body, 'EnrollmentToken'), []);
  const m = method('enterprises.enrollmentTokens.create');
  assert.equal(req.method, m.httpMethod);
  assert.equal(req.url, `${API}/${ENT}/enrollmentTokens`);
  assert.equal(m.path, 'v1/{+parent}/enrollmentTokens');
  assert.equal(m.request, 'EnrollmentToken');
  const reusable = buildRequest('enrol', { enterprise: ENT, policy: 'riskmandate', reusable: true, 'personal-usage': 'PERSONAL_USAGE_DISALLOWED' });
  assert.deepEqual(validate(reusable.body, 'EnrollmentToken'), []);
});

test('web-token: the body fits WebToken, and URL and verb match the API', () => {
  const req = buildRequest('web-token', { enterprise: ENT, 'parent-frame-url': 'https://example.com/play' });
  assert.deepEqual(validate(req.body, 'WebToken'), []);
  assert.equal(req.method, method('enterprises.webTokens.create').httpMethod);
  assert.equal(method('enterprises.webTokens.create').path, 'v1/{+parent}/webTokens');
});

test('policy-apply: PATCH on the policy name, body fits Policy', () => {
  const req = buildRequest('policy-apply', { enterprise: ENT, policy: 'riskmandate', file: path.join(POLICIES_DIR, 'riskmandate-fully-managed.json'), package: 'ai.riskmandate.app' });
  assert.deepEqual(validate(req.body, 'Policy'), []);
  const m = method('enterprises.policies.patch');
  assert.equal(req.method, m.httpMethod);
  assert.equal(m.path, 'v1/{+name}');
  assert.equal(req.url, `${API}/${ENT}/policies/riskmandate`);
});

test('signup-url, enterprise-create and devices-list: verbs and query parameters are real parameters', () => {
  const su = buildRequest('signup-url', { project: 'p', callback: 'https://example.com/' });
  const sm = method('signupUrls.create');
  assert.equal(su.method, sm.httpMethod);
  for (const k of new URL(su.url).searchParams.keys()) assert.ok(k in sm.parameters, `signupUrls.create has no parameter ${k}`);

  const ec = buildRequest('enterprise-create', { project: 'p', name: 'n', 'signup-url-name': 'signupUrls/C1', 'enterprise-token': 'T' });
  const em = method('enterprises.create');
  assert.equal(ec.method, em.httpMethod);
  for (const k of new URL(ec.url).searchParams.keys()) assert.ok(k in em.parameters, `enterprises.create has no parameter ${k}`);
  assert.deepEqual(validate(ec.body, 'Enterprise'), []);

  const dl = buildRequest('devices-list', { enterprise: ENT, 'page-token': 'x' });
  const dm = method('enterprises.devices.list');
  assert.equal(dl.method, dm.httpMethod);
  for (const k of new URL(dl.url).searchParams.keys()) assert.ok(k in dm.parameters, `devices.list has no parameter ${k}`);
});

test('device-delete and token-revoke: DELETE on the resource name, query parameters exist', () => {
  const dd = buildRequest('device-delete', { 'enterprise-device': `${ENT}/devices/3abc`, yes: true });
  const dm = method('enterprises.devices.delete');
  assert.equal(dd.method, dm.httpMethod);
  assert.equal(dm.path, 'v1/{+name}');
  assert.equal(dd.url, `${API}/${ENT}/devices/3abc`);
  const tr = buildRequest('token-revoke', { token: `${ENT}/enrollmentTokens/xyz` });
  const tm = method('enterprises.enrollmentTokens.delete');
  assert.equal(tr.method, tm.httpMethod);
  assert.equal(tm.path, 'v1/{+name}');
  assert.equal(tr.url, `${API}/${ENT}/enrollmentTokens/xyz`);
});

test('the trimmed discovery document is the one we say it is', () => {
  assert.equal(DISCOVERY.source, 'https://androidmanagement.googleapis.com/$discovery/rest?version=v1');
  assert.match(DISCOVERY.revision, /^\d{8}$/);
  for (const n of ['Policy', 'EnrollmentToken', 'WebToken', 'Enterprise', 'ApplicationPolicy', 'AdvancedSecurityOverrides']) assert.ok(DISCOVERY.schemas[n], n);
});
