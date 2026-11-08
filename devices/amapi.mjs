#!/usr/bin/env node
// amapi.mjs: a zero-dependency CLI for the Android Management API (AMAPI), for milestone M2.
// Node 18+. No npm packages. See devices/README.md for the runbook.
//
// Credentials: a service-account JSON whose path is in GOOGLE_APPLICATION_CREDENTIALS.
// The CLI never prints the key, the access token, an enrolment token or a web token.
// Secrets it must hand over (the QR payload, the web token) go only to files under devices/out/.
//
// Every command takes --dry-run: it prints the request it would send and reads no credentials.

import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { encode as qrEncode, toSvg, toPng } from './qr.mjs';

export const API = 'https://androidmanagement.googleapis.com/v1';
export const TOKEN_URL = 'https://oauth2.googleapis.com/token';
export const SCOPE = 'https://www.googleapis.com/auth/androidmanagement';
export const DEFAULT_START_URL = 'https://riskmandate.ai/';
export const DEFAULT_DEVICE_LABEL = 'riskmandate-tablet';
const HERE = path.dirname(fileURLToPath(import.meta.url));

const USAGE = `amapi.mjs: Android Management API helper (M2). Every command accepts --dry-run.

  signup-url        --project <id> --callback <https url>
  enterprise-create --project <id> --name <display> [--signup-url-name <signupUrls/...>]
                    The enterprise token is read from AMAPI_ENTERPRISE_TOKEN and the signup URL name from
                    AMAPI_SIGNUP_TOKEN (preferred: nothing in shell history or ps). The flags
                    --enterprise-token and --signup-url-name / --signup-token still work, with a warning.
  policy-apply      --enterprise <enterprises/x> --policy <name> --file <policy.json> --package <appId>
                    [--start-url <url>] [--device-label <label>]
  enrol             --enterprise <enterprises/x> --policy <name> [--duration 3600s] [--reusable]
                    (the token is one-time by default; --reusable enrols any number of devices until it expires)
                    [--personal-usage PERSONAL_USAGE_DISALLOWED_USERLESS|PERSONAL_USAGE_DISALLOWED]
  devices-list      --enterprise <enterprises/x> [--package <appId>]
  web-token         --enterprise <enterprises/x> --parent-frame-url <https url>
  device-delete     --enterprise-device <enterprises/x/devices/y> --yes   (wipes the tablet and removes it; --yes is required)
  token-revoke      --token <enterprises/x/enrollmentTokens/y>            (invalidates an enrolment token and its QR code)
  qr                --in <text file> [--ecc L|M|Q|H]   (draws any text file as QR images into the output folder)

Credentials: GOOGLE_APPLICATION_CREDENTIALS=<path to service-account json> (never in this repo).
Outputs: devices/out/ (gitignored, mode 700), or AMAPI_OUT_DIR. Files are never overwritten.`;

// ---------- small helpers ----------

export function b64url(input) {
  const buf = Buffer.isBuffer(input) ? input : Buffer.from(input);
  return buf.toString('base64').replace(/=+$/, '').replace(/\+/g, '-').replace(/\//g, '_');
}

export function parseArgs(argv) {
  const [cmd, ...rest] = argv;
  const opts = {};
  const BOOL = new Set(['dry-run', 'one-time', 'reusable', 'help', 'yes']);
  for (let i = 0; i < rest.length; i++) {
    const a = rest[i];
    if (!a.startsWith('--')) throw new UsageError(`unexpected argument: ${a}`);
    const eq = a.indexOf('=');
    const key = eq > 0 ? a.slice(2, eq) : a.slice(2);
    if (BOOL.has(key)) { opts[key] = true; continue; }
    const val = eq > 0 ? a.slice(eq + 1) : rest[++i];
    if (val === undefined) throw new UsageError(`--${key} needs a value`);
    opts[key] = val;
  }
  return { cmd, opts };
}

export class UsageError extends Error {}

function need(opts, ...keys) {
  for (const k of keys) if (!opts[k]) throw new UsageError(`missing --${k}`);
}

function q(params) {
  const u = new URLSearchParams();
  for (const [k, v] of Object.entries(params)) if (v !== undefined && v !== '') u.append(k, v);
  const s = u.toString();
  return s ? `?${s}` : '';
}

const ENTERPRISE_RE = /^enterprises\/[A-Za-z0-9_-]+$/;
const DEVICE_RE = /^enterprises\/[A-Za-z0-9_-]+\/devices\/[A-Za-z0-9_-]+$/;
const TOKEN_NAME_RE = /^enterprises\/[A-Za-z0-9_-]+\/enrollmentTokens\/[A-Za-z0-9_-]+$/;
const POLICY_RE = /^[A-Za-z0-9_.-]+$/;
const PACKAGE_RE = /^[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z][a-zA-Z0-9_]*)+$/;

function checkEnterprise(e) {
  if (!ENTERPRISE_RE.test(e)) throw new UsageError(`--enterprise must look like enterprises/LC0abc123 (got ${e})`);
}
function checkPolicy(p) {
  if (!POLICY_RE.test(p)) throw new UsageError(`--policy must be a short policy id such as riskmandate (got ${p})`);
}
function checkHttps(u, flag) {
  let parsed;
  try { parsed = new URL(u); } catch { throw new UsageError(`${flag} is not a URL`); }
  if (parsed.protocol !== 'https:') throw new UsageError(`${flag} must be https`);
}

// The same rule as the app (Links.isAllowed, as hardened): https, host exactly riskmandate.ai or
// www.riskmandate.ai, no user info, no backslash, no whitespace, port only 443.
const START_HOSTS = new Set(['riskmandate.ai', 'www.riskmandate.ai']);
export function validateStartUrl(u) {
  if (typeof u !== 'string' || !u) throw new UsageError('--start-url is empty');
  if (/[\s\\@]/.test(u)) throw new UsageError('--start-url must not contain whitespace, a backslash or @');
  let parsed;
  try { parsed = new URL(u); } catch { throw new UsageError('--start-url is not a URL'); }
  if (parsed.protocol !== 'https:') throw new UsageError('--start-url must be https');
  if (parsed.username || parsed.password) throw new UsageError('--start-url must not carry a user name or password');
  if (!START_HOSTS.has(parsed.hostname)) throw new UsageError('--start-url host must be exactly riskmandate.ai or www.riskmandate.ai');
  if (parsed.port !== '' && parsed.port !== '443') throw new UsageError('--start-url may only use port 443');
  return u;
}

// Trimmed, at most 64 characters, no control characters (mirrors the app's ManagedConfig).
export function cleanDeviceLabel(l) {
  if (typeof l !== 'string') throw new UsageError('--device-label must be text');
  const t = l.trim().slice(0, 64).trim();
  if (!t) throw new UsageError('--device-label is empty');
  if (/[\u0000-\u001f\u007f-\u009f\u200b-\u200f\u2028-\u202e\u2066-\u2069]/.test(t)) throw new UsageError('--device-label must not contain control characters');
  return t;
}

// ---------- policy ----------

// Removes every key starting with "_" (notes in the policy file), at any depth.
export function stripNotes(v) {
  if (Array.isArray(v)) return v.map(stripNotes);
  if (v && typeof v === 'object') {
    const out = {};
    for (const [k, x] of Object.entries(v)) if (!k.startsWith('_')) out[k] = stripNotes(x);
    return out;
  }
  return v;
}

// Fills __PACKAGE__, __START_URL__ and __DEVICE_LABEL__ in string values; refuses leftovers.
export function substitutePolicy(policy, { packageName, startUrl = DEFAULT_START_URL, deviceLabel = DEFAULT_DEVICE_LABEL }) {
  if (!packageName || !PACKAGE_RE.test(packageName)) throw new UsageError(`--package must be an Android application ID (got ${packageName})`);
  startUrl = validateStartUrl(startUrl);
  deviceLabel = cleanDeviceLabel(deviceLabel);
  const map = { __PACKAGE__: packageName, __START_URL__: startUrl, __DEVICE_LABEL__: deviceLabel };
  const walk = (v) => {
    if (Array.isArray(v)) return v.map(walk);
    if (v && typeof v === 'object') return Object.fromEntries(Object.entries(v).map(([k, x]) => [k, walk(x)]));
    if (typeof v === 'string') return v.replace(/__[A-Z_]+__/g, (m) => (m in map ? map[m] : m));
    return v;
  };
  const out = walk(stripNotes(policy));
  const left = JSON.stringify(out).match(/__[A-Z_]+__/g);
  if (left) throw new UsageError(`policy still has placeholders: ${[...new Set(left)].join(', ')}`);
  return out;
}

export function loadPolicy(file, subs) {
  const raw = JSON.parse(fs.readFileSync(file, 'utf8'));
  return substitutePolicy(raw, subs);
}

// ---------- QR payload ----------

// The shape AMAPI documents for a fully managed QR code. AMAPI returns its own qrCode string,
// which is what we write out; this is only used to show the shape in --dry-run.
export function qrPayloadShape(tokenPlaceholder = '<enrollment-token>') {
  return {
    'android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME': 'com.google.android.apps.work.clouddpc/.receivers.CloudDeviceAdminReceiver',
    'android.app.extra.PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM': 'I5YvS0O5hXY46mb01BlRjq4oJJGs2kuUcHvVkAPEXlg',
    'android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION': 'https://play.google.com/managed/downloadManagingApp?identifier=setup',
    'android.app.extra.PROVISIONING_ADMIN_EXTRAS_BUNDLE': {
      'com.google.android.apps.work.clouddpc.EXTRA_ENROLLMENT_TOKEN': tokenPlaceholder,
    },
  };
}

// ---------- request building (pure: no credentials, no network) ----------

export function buildRequest(cmd, opts, env = {}) {
  switch (cmd) {
    case 'signup-url': {
      need(opts, 'project', 'callback');
      checkHttps(opts.callback, '--callback');
      return { method: 'POST', url: `${API}/signupUrls${q({ projectId: opts.project, callbackUrl: opts.callback })}` };
    }
    case 'enterprise-create': {
      const warnings = [];
      // Environment variables win over flags: a flag shows in shell history and in ps.
      const enterpriseToken = env.AMAPI_ENTERPRISE_TOKEN || opts['enterprise-token'];
      if (!env.AMAPI_ENTERPRISE_TOKEN && opts['enterprise-token']) warnings.push('--enterprise-token puts the token in shell history and ps; set AMAPI_ENTERPRISE_TOKEN instead');
      const signupName = env.AMAPI_SIGNUP_TOKEN || opts['signup-url-name'] || opts['signup-token'];
      if (!env.AMAPI_SIGNUP_TOKEN && (opts['signup-url-name'] || opts['signup-token'])) warnings.push('--signup-url-name / --signup-token on the command line: prefer AMAPI_SIGNUP_TOKEN');
      need(opts, 'project', 'name');
      if (!signupName) throw new UsageError('missing the signup URL name (AMAPI_SIGNUP_TOKEN or --signup-url-name)');
      if (!enterpriseToken) throw new UsageError('missing the enterprise token (AMAPI_ENTERPRISE_TOKEN or --enterprise-token)');
      return {
        method: 'POST',
        url: `${API}/enterprises${q({ projectId: opts.project, signupUrlName: signupName, enterpriseToken })}`,
        body: { enterpriseDisplayName: opts.name },
        warnings,
      };
    }
    case 'policy-apply': {
      need(opts, 'enterprise', 'policy', 'file', 'package');
      checkEnterprise(opts.enterprise); checkPolicy(opts.policy);
      const body = loadPolicy(opts.file, { packageName: opts.package, startUrl: opts['start-url'], deviceLabel: opts['device-label'] });
      return { method: 'PATCH', url: `${API}/${opts.enterprise}/policies/${opts.policy}`, body };
    }
    case 'enrol': {
      need(opts, 'enterprise', 'policy');
      checkEnterprise(opts.enterprise); checkPolicy(opts.policy);
      const duration = opts.duration || '3600s';
      if (!/^\d+s$/.test(duration)) throw new UsageError('--duration must be seconds with an s, e.g. 3600s');
      const usage = opts['personal-usage'] || 'PERSONAL_USAGE_DISALLOWED_USERLESS';
      if (!['PERSONAL_USAGE_DISALLOWED_USERLESS', 'PERSONAL_USAGE_DISALLOWED'].includes(usage)) {
        throw new UsageError('--personal-usage must be PERSONAL_USAGE_DISALLOWED_USERLESS or PERSONAL_USAGE_DISALLOWED (fully managed)');
      }
      if (opts.reusable && opts['one-time']) throw new UsageError('--reusable and --one-time contradict each other');
      // One-time by default: a leaked or photographed QR code enrols one device at most.
      const body = { policyName: `${opts.enterprise}/policies/${opts.policy}`, duration, allowPersonalUsage: usage };
      if (!opts.reusable) body.oneTimeOnly = true;
      const warnings = opts.reusable ? [`--reusable: this token can enrol any number of devices until it expires (${duration}); keep the QR code private`] : [];
      return { method: 'POST', url: `${API}/${opts.enterprise}/enrollmentTokens`, body, warnings };
    }
    case 'device-delete': {
      need(opts, 'enterprise-device');
      if (!DEVICE_RE.test(opts['enterprise-device'])) throw new UsageError('--enterprise-device must look like enterprises/LC0abc/devices/3abc (the `name` from devices-list)');
      if (!opts.yes && !opts['dry-run']) throw new UsageError('device-delete wipes the tablet and removes it from the enterprise: add --yes to confirm (or --dry-run to look)');
      return { method: 'DELETE', url: `${API}/${opts['enterprise-device']}`, warnings: ['this wipes the tablet; it must be enrolled again from a factory reset'] };
    }
    case 'token-revoke': {
      need(opts, 'token');
      if (!TOKEN_NAME_RE.test(opts.token)) throw new UsageError('--token must look like enterprises/LC0abc/enrollmentTokens/xyz (the `name` that enrol printed)');
      return { method: 'DELETE', url: `${API}/${opts.token}` };
    }
    case 'qr': {
      need(opts, 'in');
      if (opts.ecc && !['L', 'M', 'Q', 'H'].includes(opts.ecc)) throw new UsageError('--ecc must be L, M, Q or H');
      return { method: 'LOCAL', url: 'file://' + path.resolve(opts.in) };
    }
    case 'devices-list': {
      need(opts, 'enterprise');
      checkEnterprise(opts.enterprise);
      return { method: 'GET', url: `${API}/${opts.enterprise}/devices${q({ pageSize: '100', pageToken: opts['page-token'] })}` };
    }
    case 'web-token': {
      need(opts, 'enterprise', 'parent-frame-url');
      checkEnterprise(opts.enterprise);
      checkHttps(opts['parent-frame-url'], '--parent-frame-url');
      return {
        method: 'POST',
        url: `${API}/${opts.enterprise}/webTokens`,
        body: { parentFrameUrl: opts['parent-frame-url'], enabledFeatures: ['PLAY_SEARCH', 'PRIVATE_APPS', 'MANAGED_CONFIGURATIONS'] },
      };
    }
    default:
      throw new UsageError(cmd ? `unknown command: ${cmd}` : 'no command');
  }
}

// ---------- redaction ----------

const SECRET_KEYS = new Set(['value', 'qrCode', 'access_token', 'id_token', 'refresh_token', 'private_key', 'assertion', 'enterpriseToken', 'token']);
const SECRET_QUERY = new Set(['enterpriseToken', 'token', 'access_token']);

export function redact(v) {
  if (Array.isArray(v)) return v.map(redact);
  if (v && typeof v === 'object') {
    return Object.fromEntries(Object.entries(v).map(([k, x]) => [k, SECRET_KEYS.has(k) ? '<redacted>' : redact(x)]));
  }
  return v;
}

// A short, token-scrubbed snippet of a non-JSON error body: status context without leaking a secret.
export function safeSnippet(text, max = 120) {
  const t = String(text)
    .replace(/(bearer\s+)[^\s"']+/gi, '$1<redacted>')
    .replace(/([?&;"'\s](?:enterpriseToken|token|access_token|key|api_key|assertion)["']?\s*[=:]\s*["']?)[^\s&"',}]+/gi, '$1<redacted>')
    .replace(/[A-Za-z0-9_\-.~+/=]{24,}/g, '<redacted>')
    .replace(/[\u0000-\u001f\u007f-\u009f]+/g, ' ')
    .trim();
  return t.length > max ? `${t.slice(0, max)}...` : t;
}

export function redactUrl(u) {
  const url = new URL(u);
  for (const k of [...url.searchParams.keys()]) if (SECRET_QUERY.has(k)) url.searchParams.set(k, '<redacted>');
  return decodeURI(url.toString());
}

// ---------- auth ----------

export function buildJwt(sa, nowSec = Math.floor(Date.now() / 1000)) {
  if (!sa || !sa.client_email || !sa.private_key) throw new Error('service-account JSON lacks client_email or private_key');
  const header = { alg: 'RS256', typ: 'JWT' };
  if (sa.private_key_id) header.kid = sa.private_key_id;
  const claims = { iss: sa.client_email, scope: SCOPE, aud: TOKEN_URL, iat: nowSec, exp: nowSec + 3600 };
  const input = `${b64url(JSON.stringify(header))}.${b64url(JSON.stringify(claims))}`;
  const sig = crypto.createSign('RSA-SHA256').update(input).end().sign(sa.private_key);
  return `${input}.${b64url(sig)}`;
}

function readServiceAccount(env) {
  const p = env.GOOGLE_APPLICATION_CREDENTIALS;
  if (!p) throw new UsageError('GOOGLE_APPLICATION_CREDENTIALS is not set (path to the service-account JSON, kept off the repo)');
  let sa;
  try { sa = JSON.parse(fs.readFileSync(p, 'utf8')); } catch { throw new UsageError('could not read or parse the file in GOOGLE_APPLICATION_CREDENTIALS'); }
  if (sa.type && sa.type !== 'service_account') throw new UsageError('GOOGLE_APPLICATION_CREDENTIALS is not a service-account key');
  return sa;
}

export async function getAccessToken(env, fetchImpl, nowSec) {
  const sa = readServiceAccount(env);
  const assertion = buildJwt(sa, nowSec);
  const res = await fetchImpl(TOKEN_URL, {
    method: 'POST',
    headers: { 'content-type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer', assertion }).toString(),
  });
  const data = await res.json().catch(() => ({}));
  if (!res.ok || !data.access_token) {
    throw new Error(`token exchange failed (HTTP ${res.status}): ${data.error || ''} ${data.error_description || ''}`.trim());
  }
  return data.access_token;
}

async function send(req, token, fetchImpl) {
  const init = { method: req.method, headers: { authorization: `Bearer ${token}` } };
  if (req.body !== undefined) { init.headers['content-type'] = 'application/json'; init.body = JSON.stringify(req.body); }
  const res = await fetchImpl(req.url, init);
  const text = await res.text();
  let data = {};
  let json = true;
  try { data = text ? JSON.parse(text) : {}; } catch { json = false; }
  if (!res.ok) {
    const msg = !json
      ? `non-JSON response: ${safeSnippet(text)}`
      : data.error ? `${data.error.status || ''} ${data.error.message || ''}`.trim() : JSON.stringify(redact(data));
    throw new Error(`${req.method} ${redactUrl(req.url)} failed (HTTP ${res.status}): ${msg}`);
  }
  if (!json) throw new Error(`${req.method} ${redactUrl(req.url)} returned a non-JSON body (HTTP ${res.status}): ${safeSnippet(text)}`);
  return data;
}

// ---------- outputs ----------

function outDir(env) {
  const d = env.AMAPI_OUT_DIR || path.join(HERE, 'out');
  fs.mkdirSync(d, { recursive: true, mode: 0o700 });
  fs.chmodSync(d, 0o700); // mkdir's mode does not tighten a directory that already exists
  return d;
}

function stamp(now) {
  return new Date(now).toISOString().replace(/[:.]/g, '-');
}

function writeSecretFile(file, content) {
  // 'wx': never overwrite or reuse a file (its old permissions would stay).
  try {
    fs.writeFileSync(file, content, { mode: 0o600, flag: 'wx' });
  } catch (e) {
    if (e.code === 'EEXIST') throw new Error(`${file} already exists; not overwriting (move it away and run again)`);
    throw e;
  }
  return file;
}

// Draws `text` as <base>.qr.svg and <base>.qr.png (no overwrite, mode 600: the image is a credential).
// Level L by default: the enrolment payload is long, and a screen needs no damage tolerance, so fewer, larger modules scan best.
export function writeQrImages(base, text, ecc = 'L') {
  const qr = qrEncode(text, { ecc });
  const svg = writeSecretFile(`${base}.qr.svg`, toSvg(qr));
  const png = writeSecretFile(`${base}.qr.png`, toPng(qr, { scale: 8 }));
  return { svg, png, version: qr.version, ecc: qr.ecc };
}

// ---------- main ----------

export async function main(argv, io = {}) {
  const env = io.env || process.env;
  const out = io.stdout || ((s) => process.stdout.write(`${s}\n`));
  const err = io.stderr || ((s) => process.stderr.write(`${s}\n`));
  const fetchImpl = io.fetch || globalThis.fetch;
  const now = io.now || Date.now();

  let cmd, opts;
  try {
    ({ cmd, opts } = parseArgs(argv));
    if (!cmd || cmd === '--help' || cmd === 'help' || opts.help) { out(USAGE); return cmd ? 0 : 2; }
    const req = buildRequest(cmd, opts, env);
    for (const w of req.warnings || []) err(`warning: ${w}`);

    if (opts['dry-run']) {
      const shown = { dryRun: true, command: cmd, method: req.method, url: redactUrl(req.url), headers: { authorization: 'Bearer <access-token>' } };
      if (req.body !== undefined) shown.body = req.body;
      if (cmd === 'enrol') shown.qrPayloadShape = qrPayloadShape();
      if (cmd === 'web-token') shown.iframeUrl = 'https://play.google.com/work/embedded/search?token=<web-token>&mode=SELECT';
      out(JSON.stringify(shown, null, 2));
      return 0;
    }

    const token = cmd === 'qr' ? null : await getAccessToken(env, fetchImpl, Math.floor(now / 1000));

    switch (cmd) {
      case 'signup-url': {
        const r = await send(req, token, fetchImpl);
        out(JSON.stringify({ name: r.name, url: r.url }, null, 2));
        out('Open the url, sign up, and keep `name` for enterprise-create. The enterpriseToken arrives on your callback URL.');
        return 0;
      }
      case 'enterprise-create': {
        const r = await send(req, token, fetchImpl);
        out(JSON.stringify({ name: r.name, enterpriseDisplayName: r.enterpriseDisplayName }, null, 2));
        out(`Enterprise bound. Use --enterprise ${r.name} from now on.`);
        return 0;
      }
      case 'policy-apply': {
        const r = await send(req, token, fetchImpl);
        out(JSON.stringify({ name: r.name, version: r.version, applications: (r.applications || []).map((a) => ({ packageName: a.packageName, installType: a.installType })) }, null, 2));
        return 0;
      }
      case 'enrol': {
        const r = await send(req, token, fetchImpl);
        const dir = outDir(env);
        const base = path.join(dir, `enrol-${stamp(now)}`);
        const jsonFile = writeSecretFile(`${base}.json`, `${JSON.stringify({ name: r.name, policyName: r.policyName, expirationTimestamp: r.expirationTimestamp, oneTimeOnly: !!r.oneTimeOnly, allowPersonalUsage: r.allowPersonalUsage, qrCode: r.qrCode ? JSON.parse(r.qrCode) : null }, null, 2)}\n`);
        const qrFile = r.qrCode ? writeSecretFile(`${base}.qr.txt`, r.qrCode) : null;
        const images = r.qrCode ? writeQrImages(base, r.qrCode) : null;
        out(JSON.stringify({ name: r.name, policyName: r.policyName, expirationTimestamp: r.expirationTimestamp, oneTimeOnly: !!r.oneTimeOnly }, null, 2));
        out(`QR payload written to ${jsonFile}`);
        if (qrFile) out(`Raw qrCode string written to ${qrFile}`);
        if (images) out(`QR images (QR version ${images.version}, level ${images.ecc}) written to ${images.svg} and ${images.png}: show the PNG full screen on a laptop, or print it.`);
        out('These files hold an enrolment token: do not commit, share or paste them. They expire at expirationTimestamp.');
        return 0;
      }
      case 'device-delete':
      case 'token-revoke': {
        await send(req, token, fetchImpl);
        out(cmd === 'device-delete' ? `Deleted ${opts['enterprise-device']}: the tablet is told to wipe itself (not guaranteed if it stays offline).` : `Revoked ${opts.token}: its QR code no longer enrols anything.`);
        return 0;
      }
      case 'qr': {
        const text = fs.readFileSync(opts.in, 'utf8').replace(/\r?\n$/, '');
        const dir = outDir(env);
        const images = writeQrImages(path.join(dir, `qr-${stamp(now)}`), text, opts.ecc);
        out(`QR images (QR version ${images.version}, level ${images.ecc}) written to ${images.svg} and ${images.png}`);
        return 0;
      }
      case 'devices-list': {
        const rows = [];
        let pageToken;
        do {
          const page = await send(buildRequest('devices-list', { ...opts, 'page-token': pageToken }), token, fetchImpl);
          for (const d of page.devices || []) {
            const app = opts.package ? (d.applicationReports || []).find((a) => a.packageName === opts.package) : undefined;
            rows.push({
              name: d.name, state: d.state, managementMode: d.managementMode, ownership: d.ownership,
              policy: d.appliedPolicyName, policyCompliant: d.policyCompliant, appliedState: d.appliedState,
              nonCompliance: (d.nonComplianceDetails || []).map((n) => [n.settingName, n.nonComplianceReason, n.packageName].filter(Boolean).join(' ')),
              model: d.hardwareInfo ? `${d.hardwareInfo.brand || ''} ${d.hardwareInfo.model || ''}`.trim() : undefined,
              androidVersion: d.softwareInfo && d.softwareInfo.androidVersion,
              lastStatusReportTime: d.lastStatusReportTime,
              ...(opts.package ? { app: app ? { versionName: app.versionName, versionCode: app.versionCode, state: app.state } : 'not reported' } : {}),
            });
          }
          pageToken = page.nextPageToken;
        } while (pageToken);
        out(JSON.stringify({ count: rows.length, devices: rows }, null, 2));
        return 0;
      }
      case 'web-token': {
        const r = await send(req, token, fetchImpl);
        const dir = outDir(env);
        const file = writeSecretFile(path.join(dir, `web-token-${stamp(now)}.json`), `${JSON.stringify({ name: r.name, parentFrameUrl: r.parentFrameUrl, value: r.value, iframeUrl: `https://play.google.com/work/embedded/search?token=${r.value}&mode=SELECT` }, null, 2)}\n`);
        out(JSON.stringify({ name: r.name, parentFrameUrl: r.parentFrameUrl }, null, 2));
        out(`Web token and iframe URL written to ${file}. The iframe only loads inside a page served at parentFrameUrl.`);
        return 0;
      }
    }
    return 0;
  } catch (e) {
    if (e instanceof UsageError) { err(`error: ${e.message}`); err(''); err(USAGE); return 2; }
    err(`error: ${e.message}`);
    return 1;
  }
}

const isMain = process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isMain) {
  main(process.argv.slice(2)).then((code) => { process.exitCode = code; });
}
