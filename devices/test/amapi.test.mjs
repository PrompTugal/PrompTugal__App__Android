// Run: node --test devices/test/
import { test } from 'node:test';
import assert from 'node:assert/strict';
import crypto from 'node:crypto';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import {
  buildJwt, buildRequest, substitutePolicy, stripNotes, loadPolicy, main, redact, redactUrl, parseArgs,
  API, TOKEN_URL, SCOPE, UsageError,
} from '../amapi.mjs';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const POLICY = path.join(HERE, '..', 'policies', 'riskmandate-fully-managed.json');
const PKG = 'ai.riskmandate.app';
const ENT = 'enterprises/LC0test123';

const { privateKey, publicKey } = crypto.generateKeyPairSync('rsa', { modulusLength: 2048 });
const PRIVATE_PEM = privateKey.export({ type: 'pkcs8', format: 'pem' });
const SA = {
  type: 'service_account', project_id: 'test-project', private_key_id: 'kid-123',
  private_key: PRIVATE_PEM, client_email: 'amapi@test-project.iam.gserviceaccount.com',
};

const fromB64url = (s) => Buffer.from(s.replace(/-/g, '+').replace(/_/g, '/'), 'base64');

function tmpDir() { return fs.mkdtempSync(path.join(os.tmpdir(), 'amapi-test-')); }

function writeSa(dir) {
  const p = path.join(dir, 'sa.json');
  fs.writeFileSync(p, JSON.stringify(SA));
  return p;
}

async function run(argv, { env = {}, fetch } = {}) {
  const lines = []; const errs = [];
  const code = await main(argv, {
    env, fetch: fetch || (() => { throw new Error('network must not be used'); }),
    stdout: (s) => lines.push(s), stderr: (s) => errs.push(s), now: Date.UTC(2026, 10, 8, 21, 0, 0),
  });
  return { code, stdout: lines.join('\n'), stderr: errs.join('\n') };
}

// ---------- JWT ----------

test('JWT: RS256 header, claims, and a signature that verifies with the public key', () => {
  const now = 1_800_000_000;
  const jwt = buildJwt(SA, now);
  const [h, c, s] = jwt.split('.');
  assert.ok(h && c && s);
  const header = JSON.parse(fromB64url(h));
  const claims = JSON.parse(fromB64url(c));
  assert.deepEqual(header, { alg: 'RS256', typ: 'JWT', kid: 'kid-123' });
  assert.equal(claims.iss, SA.client_email);
  assert.equal(claims.scope, SCOPE);
  assert.equal(claims.scope, 'https://www.googleapis.com/auth/androidmanagement');
  assert.equal(claims.aud, TOKEN_URL);
  assert.equal(claims.aud, 'https://oauth2.googleapis.com/token');
  assert.equal(claims.iat, now);
  assert.equal(claims.exp, now + 3600);
  const ok = crypto.createVerify('RSA-SHA256').update(`${h}.${c}`).end().verify(publicKey, fromB64url(s));
  assert.ok(ok, 'signature verifies');
  assert.ok(!/[=+/]/.test(jwt), 'base64url without padding');
});

test('JWT: a tampered payload does not verify', () => {
  const [h, , s] = buildJwt(SA, 1).split('.');
  const forged = Buffer.from(JSON.stringify({ iss: 'evil' })).toString('base64url');
  assert.equal(crypto.createVerify('RSA-SHA256').update(`${h}.${forged}`).end().verify(publicKey, fromB64url(s)), false);
});

test('JWT: refuses a service account without a key', () => {
  assert.throws(() => buildJwt({ client_email: 'x' }), /private_key/);
});

// ---------- request building ----------

test('signup-url request', () => {
  const r = buildRequest('signup-url', { project: 'my-proj', callback: 'https://example.com/cb' });
  assert.equal(r.method, 'POST');
  const u = new URL(r.url);
  assert.equal(u.origin + u.pathname, `${API}/signupUrls`);
  assert.equal(u.searchParams.get('projectId'), 'my-proj');
  assert.equal(u.searchParams.get('callbackUrl'), 'https://example.com/cb');
  assert.equal(r.body, undefined);
  assert.throws(() => buildRequest('signup-url', { project: 'p', callback: 'http://x' }), /https/);
});

test('enterprise-create request, and --signup-token as alias', () => {
  for (const key of ['signup-url-name', 'signup-token']) {
    const r = buildRequest('enterprise-create', { project: 'p', [key]: 'signupUrls/C123', 'enterprise-token': 'ET', name: 'PrompTugal Devices' });
    const u = new URL(r.url);
    assert.equal(r.method, 'POST');
    assert.equal(u.pathname, '/v1/enterprises');
    assert.equal(u.searchParams.get('signupUrlName'), 'signupUrls/C123');
    assert.equal(u.searchParams.get('enterpriseToken'), 'ET');
    assert.equal(u.searchParams.get('projectId'), 'p');
    assert.deepEqual(r.body, { enterpriseDisplayName: 'PrompTugal Devices' });
  }
});

test('policy-apply request substitutes the package and strips notes', () => {
  const r = buildRequest('policy-apply', { enterprise: ENT, policy: 'riskmandate', file: POLICY, package: 'ai.example.other' });
  assert.equal(r.method, 'PATCH');
  assert.equal(r.url, `${API}/${ENT}/policies/riskmandate`);
  const app = r.body.applications.find((a) => a.packageName === 'ai.example.other');
  assert.ok(app);
  assert.equal(app.installType, 'FORCE_INSTALLED');
  assert.equal(app.autoUpdateMode, 'AUTO_UPDATE_HIGH_PRIORITY');
  assert.deepEqual(app.managedConfiguration, { start_url: 'https://riskmandate.ai/', device_label: 'riskmandate-tablet' });
  assert.ok(!JSON.stringify(r.body).includes('_comment'));
  assert.ok(!/__[A-Z_]+__/.test(JSON.stringify(r.body)));
  assert.equal(r.body.frpAdminEmails, undefined, 'FRP is a Dinis decision, not set silently');
});

test('enrol request: defaults and options', () => {
  const r = buildRequest('enrol', { enterprise: ENT, policy: 'riskmandate' });
  assert.equal(r.method, 'POST');
  assert.equal(r.url, `${API}/${ENT}/enrollmentTokens`);
  assert.deepEqual(r.body, { policyName: `${ENT}/policies/riskmandate`, duration: '3600s', allowPersonalUsage: 'PERSONAL_USAGE_DISALLOWED_USERLESS' });
  const r2 = buildRequest('enrol', { enterprise: ENT, policy: 'riskmandate', duration: '600s', 'one-time': true, 'personal-usage': 'PERSONAL_USAGE_DISALLOWED' });
  assert.deepEqual(r2.body, { policyName: `${ENT}/policies/riskmandate`, duration: '600s', allowPersonalUsage: 'PERSONAL_USAGE_DISALLOWED', oneTimeOnly: true });
  assert.throws(() => buildRequest('enrol', { enterprise: ENT, policy: 'p', duration: '1h' }), /duration/);
  assert.throws(() => buildRequest('enrol', { enterprise: ENT, policy: 'p', 'personal-usage': 'PERSONAL_USAGE_ALLOWED' }), /fully managed/);
});

test('devices-list request', () => {
  const r = buildRequest('devices-list', { enterprise: ENT });
  assert.equal(r.method, 'GET');
  assert.equal(r.url, `${API}/${ENT}/devices?pageSize=100`);
});

test('web-token request', () => {
  const r = buildRequest('web-token', { enterprise: ENT, 'parent-frame-url': 'https://devices.example.com/play' });
  assert.equal(r.method, 'POST');
  assert.equal(r.url, `${API}/${ENT}/webTokens`);
  assert.equal(r.body.parentFrameUrl, 'https://devices.example.com/play');
  assert.ok(r.body.enabledFeatures.includes('PRIVATE_APPS'));
});

test('bad input is refused', () => {
  assert.throws(() => buildRequest('nope', {}), UsageError);
  assert.throws(() => buildRequest('enrol', { enterprise: 'LC0abc', policy: 'p' }), /enterprises\//);
  assert.throws(() => buildRequest('enrol', { enterprise: ENT, policy: 'a/b' }), /policy/);
  assert.throws(() => buildRequest('devices-list', {}), /--enterprise/);
  assert.throws(() => parseArgs(['enrol', '--policy']), /needs a value/);
});

// ---------- policy substitution ----------

test('substitutePolicy fills placeholders and refuses leftovers or a bad package', () => {
  const p = { _note: 'x', applications: [{ packageName: '__PACKAGE__', managedConfiguration: { start_url: '__START_URL__', device_label: '__DEVICE_LABEL__', _c: 1 } }] };
  const out = substitutePolicy(p, { packageName: 'ai.riskmandate.app', startUrl: 'https://riskmandate.ai/x', deviceLabel: 'tab-01' });
  assert.deepEqual(out, { applications: [{ packageName: 'ai.riskmandate.app', managedConfiguration: { start_url: 'https://riskmandate.ai/x', device_label: 'tab-01' } }] });
  assert.throws(() => substitutePolicy({ a: '__OTHER__' }, { packageName: PKG }), /placeholders: __OTHER__/);
  assert.throws(() => substitutePolicy(p, { packageName: 'not a package' }), /application ID/);
  assert.throws(() => substitutePolicy(p, {}), /application ID/);
});

test('the shipped policy file is valid and fully managed as specified', () => {
  const raw = JSON.parse(fs.readFileSync(POLICY, 'utf8'));
  assert.ok(JSON.stringify(raw).includes('__PACKAGE__'));
  const p = loadPolicy(POLICY, { packageName: PKG });
  assert.equal(p.playStoreMode, 'WHITELIST');
  assert.equal(p.systemUpdate.type, 'WINDOWED');
  assert.ok(p.systemUpdate.startMinutes >= 0 && p.systemUpdate.endMinutes <= 1439);
  assert.equal(p.statusReportingSettings.applicationReportsEnabled, true);
  assert.equal(p.applications[0].packageName, PKG);
  assert.equal(p.frpAdminEmails, undefined);
  assert.deepEqual(stripNotes({ _a: 1, b: [{ _c: 2, d: 3 }] }), { b: [{ d: 3 }] });
});

// ---------- dry-run via main ----------

const DRY = [
  ['signup-url', '--project', 'p', '--callback', 'https://example.com/cb'],
  ['enterprise-create', '--project', 'p', '--signup-url-name', 'signupUrls/C1', '--enterprise-token', 'SECRET-ENTERPRISE-TOKEN', '--name', 'PrompTugal'],
  ['policy-apply', '--enterprise', ENT, '--policy', 'riskmandate', '--file', POLICY, '--package', PKG],
  ['enrol', '--enterprise', ENT, '--policy', 'riskmandate'],
  ['devices-list', '--enterprise', ENT],
  ['web-token', '--enterprise', ENT, '--parent-frame-url', 'https://example.com/play'],
];

for (const argv of DRY) {
  test(`dry-run ${argv[0]} prints the request and touches neither credentials nor network`, async () => {
    const r = await run([...argv, '--dry-run'], { env: { GOOGLE_APPLICATION_CREDENTIALS: '/nonexistent/should-not-be-read.json' } });
    assert.equal(r.code, 0, r.stderr);
    const shown = JSON.parse(r.stdout);
    assert.equal(shown.dryRun, true);
    assert.equal(shown.command, argv[0]);
    assert.ok(shown.url.startsWith(API));
    assert.equal(shown.headers.authorization, 'Bearer <access-token>');
    assert.ok(!r.stdout.includes('SECRET-ENTERPRISE-TOKEN'), 'enterprise token redacted');
    if (argv[0] === 'enrol') assert.ok(shown.qrPayloadShape['android.app.extra.PROVISIONING_ADMIN_EXTRAS_BUNDLE']);
  });
}

// ---------- live path with a fake network: nothing secret reaches stdout ----------

function fakeFetch(calls) {
  return async (url, init = {}) => {
    calls.push({ url, init });
    const json = (status, body) => ({ ok: status < 400, status, json: async () => body, text: async () => JSON.stringify(body) });
    if (url === TOKEN_URL) return json(200, { access_token: 'ya29.SECRET-ACCESS-TOKEN', expires_in: 3599, token_type: 'Bearer' });
    if (url.includes('/enrollmentTokens')) {
      const qr = JSON.stringify({ 'android.app.extra.PROVISIONING_ADMIN_EXTRAS_BUNDLE': { 'com.google.android.apps.work.clouddpc.EXTRA_ENROLLMENT_TOKEN': 'SECRET-ENROL-TOKEN' } });
      return json(200, { name: `${ENT}/enrollmentTokens/abc`, value: 'SECRET-ENROL-TOKEN', qrCode: qr, policyName: `${ENT}/policies/riskmandate`, expirationTimestamp: '2026-11-08T22:00:00Z' });
    }
    if (url.includes('/webTokens')) return json(200, { name: `${ENT}/webTokens/w1`, value: 'SECRET-WEB-TOKEN', parentFrameUrl: 'https://example.com/play' });
    if (url.includes('/devices')) return json(200, { devices: [{ name: `${ENT}/devices/d1`, state: 'ACTIVE', managementMode: 'DEVICE_OWNER', applicationReports: [{ packageName: PKG, versionName: '0.1.7', versionCode: 7, state: 'INSTALLED' }] }] });
    if (url.includes('/policies/')) return json(200, { name: `${ENT}/policies/riskmandate`, version: '1', applications: JSON.parse(init.body).applications });
    if (url.includes('/signupUrls')) return json(200, { name: 'signupUrls/C1', url: 'https://enterprise.google.com/signup/android/email?x=1' });
    if (url.includes('/enterprises?')) return json(200, { name: ENT, enterpriseDisplayName: 'PrompTugal' });
    return json(404, { error: { status: 'NOT_FOUND', message: 'no' } });
  };
}

const SECRETS = ['SECRET-ACCESS-TOKEN', 'SECRET-ENROL-TOKEN', 'SECRET-WEB-TOKEN', 'SECRET-ENTERPRISE-TOKEN', 'BEGIN PRIVATE KEY', SA.private_key.split('\n')[1]];

for (const argv of DRY) {
  test(`live ${argv[0]} (fake network): authenticates and prints no secret`, async () => {
    const dir = tmpDir();
    const calls = [];
    const r = await run([...argv, ...(argv[0] === 'devices-list' ? ['--package', PKG] : [])], {
      env: { GOOGLE_APPLICATION_CREDENTIALS: writeSa(dir), AMAPI_OUT_DIR: path.join(dir, 'out') }, fetch: fakeFetch(calls),
    });
    assert.equal(r.code, 0, r.stderr);
    for (const s of SECRETS) {
      assert.ok(!r.stdout.includes(s), `stdout leaks ${s.slice(0, 20)}`);
      assert.ok(!r.stderr.includes(s), `stderr leaks ${s.slice(0, 20)}`);
    }
    assert.equal(calls[0].url, TOKEN_URL);
    const form = new URLSearchParams(calls[0].init.body);
    assert.equal(form.get('grant_type'), 'urn:ietf:params:oauth:grant-type:jwt-bearer');
    const claims = JSON.parse(fromB64url(form.get('assertion').split('.')[1]));
    assert.equal(claims.scope, SCOPE);
    assert.equal(calls[1].init.headers.authorization, 'Bearer ya29.SECRET-ACCESS-TOKEN');

    if (argv[0] === 'enrol') {
      const files = fs.readdirSync(path.join(dir, 'out'));
      const json = files.find((f) => f.endsWith('.json'));
      const qr = files.find((f) => f.endsWith('.qr.txt'));
      assert.ok(json && qr);
      assert.ok(r.stdout.includes(path.join(dir, 'out', json)), 'prints where the payload is');
      assert.ok(fs.readFileSync(path.join(dir, 'out', qr), 'utf8').includes('SECRET-ENROL-TOKEN'), 'payload file holds the token');
      assert.equal(fs.statSync(path.join(dir, 'out', qr)).mode & 0o077, 0, 'payload file is private (0600)');
    }
    if (argv[0] === 'web-token') {
      const f = fs.readdirSync(path.join(dir, 'out')).find((x) => x.startsWith('web-token-'));
      assert.ok(fs.readFileSync(path.join(dir, 'out', f), 'utf8').includes('embedded/search?token=SECRET-WEB-TOKEN'));
    }
    if (argv[0] === 'devices-list') assert.equal(JSON.parse(r.stdout).devices[0].app.versionCode, 7);
    fs.rmSync(dir, { recursive: true, force: true });
  });
}

test('missing credentials gives a clear error and no secret', async () => {
  const r = await run(['devices-list', '--enterprise', ENT], { env: {} });
  assert.equal(r.code, 2);
  assert.match(r.stderr, /GOOGLE_APPLICATION_CREDENTIALS is not set/);
});

test('an API error is reported with the URL redacted', async () => {
  const dir = tmpDir();
  const fetch = async (url) => (url === TOKEN_URL
    ? { ok: true, status: 200, json: async () => ({ access_token: 'ya29.SECRET-ACCESS-TOKEN' }) }
    : { ok: false, status: 403, text: async () => JSON.stringify({ error: { status: 'PERMISSION_DENIED', message: 'Caller lacks permission' } }) });
  const r = await run(['enterprise-create', '--project', 'p', '--signup-url-name', 's', '--enterprise-token', 'SECRET-ENTERPRISE-TOKEN', '--name', 'n'], {
    env: { GOOGLE_APPLICATION_CREDENTIALS: writeSa(dir) }, fetch,
  });
  assert.equal(r.code, 1);
  assert.match(r.stderr, /PERMISSION_DENIED/);
  assert.ok(!r.stderr.includes('SECRET'));
  fs.rmSync(dir, { recursive: true, force: true });
});

test('redact helpers', () => {
  assert.deepEqual(redact({ value: 'x', qrCode: 'y', name: 'n', nested: [{ access_token: 'z' }] }), { value: '<redacted>', qrCode: '<redacted>', name: 'n', nested: [{ access_token: '<redacted>' }] });
  assert.ok(redactUrl(`${API}/enterprises?enterpriseToken=abc&projectId=p`).includes('enterpriseToken=<redacted>'));
});
