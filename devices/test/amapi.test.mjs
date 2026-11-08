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
  API, TOKEN_URL, SCOPE, UsageError, validateStartUrl, cleanDeviceLabel, safeSnippet,
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
  assert.deepEqual(r.body, { policyName: `${ENT}/policies/riskmandate`, duration: '3600s', allowPersonalUsage: 'PERSONAL_USAGE_DISALLOWED_USERLESS', oneTimeOnly: true });
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
      const svg = files.find((f) => f.endsWith('.qr.svg'));
      const png = files.find((f) => f.endsWith('.qr.png'));
      assert.ok(svg && png, 'enrol draws the QR images itself');
      for (const f of [svg, png]) assert.equal(fs.statSync(path.join(dir, 'out', f)).mode & 0o077, 0, `${f} is private (0600)`);
      assert.ok(r.stdout.includes(path.join(dir, 'out', png)), 'prints where the PNG is');
      assert.ok(!r.stdout.includes('SECRET-ENROL-TOKEN'));
      assert.deepEqual([...fs.readFileSync(path.join(dir, 'out', png)).subarray(1, 4)], [0x50, 0x4e, 0x47]);
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

// ---------- security review fixes (A-3, A-7, A-9, A-10) ----------

test('A-3: enrolment tokens are one-time by default; --reusable opts out, with a warning', () => {
  const d = buildRequest('enrol', { enterprise: ENT, policy: 'p' });
  assert.equal(d.body.oneTimeOnly, true);
  assert.deepEqual(d.warnings, []);
  const r = buildRequest('enrol', { enterprise: ENT, policy: 'p', reusable: true });
  assert.equal(r.body.oneTimeOnly, undefined);
  assert.match(r.warnings[0], /any number of devices/);
  assert.equal(buildRequest('enrol', { enterprise: ENT, policy: 'p', 'one-time': true }).body.oneTimeOnly, true);
  assert.throws(() => buildRequest('enrol', { enterprise: ENT, policy: 'p', reusable: true, 'one-time': true }), /contradict/);
  assert.equal(parseArgs(['enrol', '--reusable']).opts.reusable, true);
});

test('A-7: tokens come from the environment first; flags still work with a warning', () => {
  const base = { project: 'p', name: 'n' };
  const fromEnv = buildRequest('enterprise-create', base, { AMAPI_ENTERPRISE_TOKEN: 'ET-ENV', AMAPI_SIGNUP_TOKEN: 'signupUrls/CENV' });
  const u = new URL(fromEnv.url);
  assert.equal(u.searchParams.get('enterpriseToken'), 'ET-ENV');
  assert.equal(u.searchParams.get('signupUrlName'), 'signupUrls/CENV');
  assert.deepEqual(fromEnv.warnings, []);
  const both = buildRequest('enterprise-create', { ...base, 'enterprise-token': 'ET-FLAG', 'signup-url-name': 'signupUrls/CFLAG' }, { AMAPI_ENTERPRISE_TOKEN: 'ET-ENV', AMAPI_SIGNUP_TOKEN: 'signupUrls/CENV' });
  assert.equal(new URL(both.url).searchParams.get('enterpriseToken'), 'ET-ENV', 'env wins');
  const flags = buildRequest('enterprise-create', { ...base, 'enterprise-token': 'ET-FLAG', 'signup-token': 'signupUrls/CFLAG' }, {});
  assert.equal(new URL(flags.url).searchParams.get('enterpriseToken'), 'ET-FLAG');
  assert.equal(flags.warnings.length, 2);
  assert.throws(() => buildRequest('enterprise-create', base, {}), /signup URL name/);
  assert.throws(() => buildRequest('enterprise-create', { ...base, 'signup-url-name': 's' }, {}), /enterprise token/);
});

test('A-7: a flag token draws a warning on stderr, never the token itself', async () => {
  const r = await run(['enterprise-create', '--project', 'p', '--signup-url-name', 's', '--enterprise-token', 'SECRET-ENTERPRISE-TOKEN', '--name', 'n', '--dry-run'], { env: {} });
  assert.equal(r.code, 0, r.stderr);
  assert.match(r.stderr, /warning: --enterprise-token/);
  assert.ok(!r.stderr.includes('SECRET-ENTERPRISE-TOKEN'));
  const e = await run(['enterprise-create', '--project', 'p', '--name', 'n', '--dry-run'], { env: { AMAPI_ENTERPRISE_TOKEN: 'SECRET-ENTERPRISE-TOKEN', AMAPI_SIGNUP_TOKEN: 'signupUrls/C1' } });
  assert.equal(e.code, 0, e.stderr);
  assert.equal(e.stderr, '');
  assert.ok(!e.stdout.includes('SECRET-ENTERPRISE-TOKEN'));
});

test('A-9: --start-url follows the app rule', () => {
  for (const ok of ['https://riskmandate.ai/', 'https://www.riskmandate.ai/x?y=1#z', 'https://RiskMandate.ai:443/', 'https://riskmandate.ai']) {
    assert.equal(validateStartUrl(ok), ok);
  }
  for (const bad of [
    'http://riskmandate.ai/', 'https://evil.com/', 'https://riskmandate.ai.evil.com/', 'https://sub.riskmandate.ai/',
    'https://riskmandate.ai@evil.com/', 'https://evil.com@riskmandate.ai/', 'https://riskmandate.ai\\@evil.com/', 'https://riskmandate.ai:8443/',
    'https://riskmandate.ai/ x', 'https://riskmandate.ai/\n', 'https://evil.com/?https://riskmandate.ai/', 'javascript:alert(1)', 'not a url', '',
  ]) {
    assert.throws(() => validateStartUrl(bad), UsageError, bad);
  }
  assert.throws(() => buildRequest('policy-apply', { enterprise: ENT, policy: 'p', file: POLICY, package: PKG, 'start-url': 'https://evil.com/' }), /riskmandate\.ai/);
  const ok = buildRequest('policy-apply', { enterprise: ENT, policy: 'p', file: POLICY, package: PKG, 'start-url': 'https://www.riskmandate.ai/' });
  assert.ok(JSON.stringify(ok.body).includes('https://www.riskmandate.ai/'));
});

test('A-9: --device-label is trimmed, capped at 64 and free of control characters', () => {
  assert.equal(cleanDeviceLabel('  lobby tablet  '), 'lobby tablet');
  assert.equal(cleanDeviceLabel('x'.repeat(100)).length, 64);
  assert.throws(() => cleanDeviceLabel('a\u0000b'), /control/);
  assert.throws(() => cleanDeviceLabel('a\nb'), /control/);
  assert.throws(() => cleanDeviceLabel('a\u202ab'), /control/);
  assert.throws(() => cleanDeviceLabel('   '), /empty/);
  const r = buildRequest('policy-apply', { enterprise: ENT, policy: 'p', file: POLICY, package: PKG, 'device-label': ` ${'y'.repeat(80)} ` });
  assert.ok(JSON.stringify(r.body).includes('y'.repeat(64)) && !JSON.stringify(r.body).includes('y'.repeat(65)));
});

test('A-10: a non-JSON error body is shown as status plus a short redacted snippet', async () => {
  const dir = tmpDir();
  const body = `<html>denied token=SECRET-RAW-TOKEN Bearer SECRET-BEARER ${'A1b2'.repeat(10)} ${'x'.repeat(400)}</html>`;
  const fetch = async (url) => (url === TOKEN_URL
    ? { ok: true, status: 200, json: async () => ({ access_token: 'ya29.SECRET-ACCESS-TOKEN' }) }
    : { ok: false, status: 502, text: async () => body });
  const r = await run(['devices-list', '--enterprise', ENT], { env: { GOOGLE_APPLICATION_CREDENTIALS: writeSa(dir) }, fetch });
  assert.equal(r.code, 1);
  assert.match(r.stderr, /HTTP 502/);
  assert.match(r.stderr, /non-JSON/);
  for (const s of ['SECRET-RAW-TOKEN', 'SECRET-BEARER', 'A1b2'.repeat(10), 'x'.repeat(200)]) assert.ok(!r.stderr.includes(s), s.slice(0, 12));
  assert.ok(r.stderr.length < 500);
  fs.rmSync(dir, { recursive: true, force: true });
});

test('A-10: a 200 with a non-JSON body is an error, not silent success', async () => {
  const dir = tmpDir();
  const fetch = async (url) => (url === TOKEN_URL
    ? { ok: true, status: 200, json: async () => ({ access_token: 'ya29.SECRET-ACCESS-TOKEN' }) }
    : { ok: true, status: 200, text: async () => '<html>captive portal</html>' });
  const r = await run(['devices-list', '--enterprise', ENT], { env: { GOOGLE_APPLICATION_CREDENTIALS: writeSa(dir) }, fetch });
  assert.equal(r.code, 1);
  assert.match(r.stderr, /non-JSON/);
  fs.rmSync(dir, { recursive: true, force: true });
});

test('A-10: the out dir is tightened to 700 when it exists, and existing files are never overwritten', async () => {
  const dir = tmpDir();
  const out = path.join(dir, 'out');
  fs.mkdirSync(out, { mode: 0o755 });
  fs.chmodSync(out, 0o755);
  const argv = ['enrol', '--enterprise', ENT, '--policy', 'riskmandate'];
  const env = { GOOGLE_APPLICATION_CREDENTIALS: writeSa(dir), AMAPI_OUT_DIR: out };
  const r = await run(argv, { env, fetch: fakeFetch([]) });
  assert.equal(r.code, 0, r.stderr);
  assert.equal(fs.statSync(out).mode & 0o077, 0, 'out dir is 700');
  const before = fs.readdirSync(out).map((f) => [f, fs.readFileSync(path.join(out, f), 'utf8')]);
  const again = await run(argv, { env, fetch: fakeFetch([]) });
  assert.equal(again.code, 1);
  assert.match(again.stderr, /already exists; not overwriting/);
  assert.deepEqual(fs.readdirSync(out).map((f) => [f, fs.readFileSync(path.join(out, f), 'utf8')]), before);
  // a pre-existing, world-readable file at the target name is refused, not reused
  const victim = path.join(out, 'web-token-2026-11-08T21-00-00-000Z.json');
  fs.writeFileSync(victim, 'old', { mode: 0o644 });
  const w = await run(['web-token', '--enterprise', ENT, '--parent-frame-url', 'https://example.com/play'], { env, fetch: fakeFetch([]) });
  assert.equal(w.code, 1);
  assert.equal(fs.readFileSync(victim, 'utf8'), 'old');
  fs.rmSync(dir, { recursive: true, force: true });
});

test('qr command: draws a text file as SVG and PNG, needs no credentials, refuses to overwrite', async () => {
  const dir = tmpDir();
  const input = path.join(dir, 'in.txt');
  fs.writeFileSync(input, 'hello rehearsal\n');
  const env = { AMAPI_OUT_DIR: path.join(dir, 'out') };
  const r = await run(['qr', '--in', input], { env });
  assert.equal(r.code, 0, r.stderr);
  const files = fs.readdirSync(path.join(dir, 'out'));
  assert.equal(files.filter((f) => f.endsWith('.qr.svg')).length, 1);
  assert.equal(files.filter((f) => f.endsWith('.qr.png')).length, 1);
  const again = await run(['qr', '--in', input], { env });
  assert.equal(again.code, 1);
  assert.match(again.stderr, /already exists/);
  assert.equal((await run(['qr', '--in', input, '--ecc', 'Z'], { env })).code, 2);
  fs.rmSync(dir, { recursive: true, force: true });
});

test('device-delete needs --yes, validates the name, and dry-run shows the request without credentials', async () => {
  const dev = `${ENT}/devices/3abc`;
  const no = await run(['device-delete', '--enterprise-device', dev]);
  assert.equal(no.code, 2);
  assert.match(no.stderr, /--yes/);
  assert.equal((await run(['device-delete', '--enterprise-device', 'devices/x', '--yes'])).code, 2);
  const dry = await run(['device-delete', '--enterprise-device', dev, '--dry-run']);
  assert.equal(dry.code, 0);
  assert.equal(JSON.parse(dry.stdout).method, 'DELETE');
  const dir = tmpDir();
  const calls = [];
  const fetch = async (url, init) => {
    calls.push({ url, init });
    return url === TOKEN_URL ? { ok: true, status: 200, json: async () => ({ access_token: 'ya29.T' }) } : { ok: true, status: 200, text: async () => '{}' };
  };
  const ok = await run(['device-delete', '--enterprise-device', dev, '--yes'], { env: { GOOGLE_APPLICATION_CREDENTIALS: writeSa(dir) }, fetch });
  assert.equal(ok.code, 0, ok.stderr);
  assert.equal(calls[1].init.method, 'DELETE');
  assert.equal(calls[1].url, `${API}/${dev}`);
  fs.rmSync(dir, { recursive: true, force: true });
});

test('token-revoke: DELETE on the token name; refuses a malformed name', async () => {
  assert.equal((await run(['token-revoke', '--token', 'nonsense'])).code, 2);
  const dry = await run(['token-revoke', '--token', `${ENT}/enrollmentTokens/xyz`, '--dry-run']);
  assert.equal(dry.code, 0);
  assert.equal(JSON.parse(dry.stdout).url, `${API}/${ENT}/enrollmentTokens/xyz`);
});
