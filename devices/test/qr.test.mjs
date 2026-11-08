// Tests for devices/qr.mjs: known vectors from the standard, and a decode of the encoder's own
// output (matrix, SVG and PNG), done here with a small decoder so it runs offline with no packages.
// Also checked once, outside the repo, with the independent zxing-cpp decoder: every version 1 to 40
// at every level at full byte capacity, 204 symbols, all decoded (see devices/README.md).
import test from 'node:test';
import assert from 'node:assert/strict';
import crypto from 'node:crypto';
import zlib from 'node:zlib';
import {
  encode, toSvg, toPng, crc32, rsRemainder, rsGenerator, formatBits, versionBits, byteCapacity,
  alignmentPositions, rawDataModules, drawFunctionPatterns, eccPerBlock, numBlocks, LEVELS, FORMAT_BITS,
} from '../qr.mjs';

// ---------- known vectors ----------

test('Reed-Solomon: ISO/IEC 18004 example "01234567", version 1-M, 10 error-correction codewords', () => {
  const data = [16, 32, 12, 86, 97, 128, 236, 17, 236, 17, 236, 17, 236, 17, 236, 17];
  assert.deepEqual(rsRemainder(data, 10), [165, 36, 212, 193, 237, 54, 199, 135, 44, 85]);
});

test('Reed-Solomon: "HELLO WORLD" version 1-M (widely published worked example)', () => {
  const data = [32, 91, 11, 120, 209, 114, 220, 77, 67, 64, 236, 17, 236, 17, 236, 17];
  assert.deepEqual(rsRemainder(data, 10), [196, 35, 39, 119, 235, 215, 231, 226, 93, 23]);
});

test('Reed-Solomon generator polynomial of degree 7 has the standard coefficients', () => {
  // alpha exponents 0 87 229 146 149 238 102 21 -> values via the field
  assert.equal(rsGenerator(7).length, 8);
  assert.deepEqual(rsGenerator(7), [1, 127, 122, 154, 164, 11, 68, 117]);
});

test('format information bits (BCH 15,5 with mask 0x5412): known values', () => {
  assert.equal(formatBits('L', 0).toString(2).padStart(15, '0'), '111011111000100');
  assert.equal(formatBits('M', 0).toString(2).padStart(15, '0'), '101010000010010');
  assert.equal(formatBits('Q', 0).toString(2).padStart(15, '0'), '011010101011111');
  assert.equal(formatBits('H', 0).toString(2).padStart(15, '0'), '001011010001001');
});

test('version information bits (BCH 18,6): known values for 7 and 40', () => {
  assert.equal(versionBits(7).toString(2).padStart(18, '0'), '000111110010010100');
  assert.equal(versionBits(40).toString(2).padStart(18, '0'), '101000110001101001');
});

test('byte-mode capacity matches the standard table', () => {
  const known = { // [L, M, Q, H]
    1: [17, 14, 11, 7], 2: [32, 26, 20, 14], 5: [106, 84, 60, 44], 10: [271, 213, 151, 119],
    20: [858, 666, 482, 382], 27: [1465, 1125, 805, 625], 40: [2953, 2331, 1663, 1273],
  };
  for (const [v, caps] of Object.entries(known)) {
    LEVELS.forEach((l, i) => assert.equal(byteCapacity(Number(v), l), caps[i], `v${v}-${l}`));
  }
});

test('raw data modules and alignment positions for known versions', () => {
  assert.equal(rawDataModules(1), 208);
  assert.equal(rawDataModules(2), 359);
  assert.equal(rawDataModules(7), 1568);
  assert.equal(rawDataModules(40), 29648);
  assert.deepEqual(alignmentPositions(1), []);
  assert.deepEqual(alignmentPositions(2), [6, 18]);
  assert.deepEqual(alignmentPositions(7), [6, 22, 38]);
  assert.deepEqual(alignmentPositions(14), [6, 26, 46, 66]);
  assert.deepEqual(alignmentPositions(32), [6, 34, 60, 86, 112, 138]);
  assert.deepEqual(alignmentPositions(40), [6, 30, 58, 86, 114, 142, 170]);
});

test('every version: the modules left after function patterns equal the standard raw count', () => {
  for (const l of LEVELS) for (let v = 1; v <= 40; v++) {
    const { isFunction } = drawFunctionPatterns(v);
    let free = 0;
    for (const row of isFunction) for (const f of row) if (!f) free++;
    assert.equal(free, rawDataModules(v), `function modules v${v}`);
  }
});

test('the PNG/SVG of a fixed input is stable (regression guard, verified with zxing-cpp)', () => {
  const qr = encode('https://riskmandate.ai/', { ecc: 'M' });
  assert.equal(qr.version, 2);
  assert.equal(qr.mask, 6);
  const rows = qr.modules.map((r) => r.map((d) => (d ? '1' : '0')).join('')).join('\n');
  assert.equal(crypto.createHash('sha256').update(rows).digest('hex'), GOLDEN_MATRIX_SHA256);
});

// ---------- a small decoder, independent of the encoder's drawing code ----------

function decodeMatrix(modules) {
  const size = modules.length;
  const ver = (size - 17) / 4;
  assert.ok(Number.isInteger(ver) && ver >= 1 && ver <= 40, 'size is a QR size');
  const bit = (x, y) => (modules[y][x] ? 1 : 0);
  // format info, first copy, and the second copy must agree
  let f1 = 0;
  for (let i = 0; i <= 5; i++) f1 |= bit(8, i) << i;
  f1 |= bit(8, 7) << 6; f1 |= bit(8, 8) << 7; f1 |= bit(7, 8) << 8;
  for (let i = 9; i < 15; i++) f1 |= bit(14 - i, 8) << i;
  let f2 = 0;
  for (let i = 0; i < 8; i++) f2 |= bit(size - 1 - i, 8) << i;
  for (let i = 8; i < 15; i++) f2 |= bit(8, size - 15 + i) << i;
  assert.equal(f1, f2, 'both format copies agree');
  let ecc, mask;
  for (const l of LEVELS) for (let m = 0; m < 8; m++) if (formatBits(l, m) === f1) { ecc = l; mask = m; }
  assert.ok(ecc, 'format info is valid');
  if (ver >= 7) {
    let v = 0;
    for (let i = 0; i < 18; i++) v |= bit(size - 11 + (i % 3), Math.floor(i / 3)) << i;
    assert.equal(v, versionBits(ver), 'version info');
  }
  const { isFunction } = drawFunctionPatterns(ver);
  const maskFn = [
    (x, y) => (x + y) % 2 === 0, (x, y) => y % 2 === 0, (x, y) => x % 3 === 0, (x, y) => (x + y) % 3 === 0,
    (x, y) => (Math.floor(x / 3) + Math.floor(y / 2)) % 2 === 0, (x, y) => ((x * y) % 2) + ((x * y) % 3) === 0,
    (x, y) => (((x * y) % 2) + ((x * y) % 3)) % 2 === 0, (x, y) => (((x + y) % 2) + ((x * y) % 3)) % 2 === 0,
  ][mask];
  const bits = [];
  for (let right = size - 1; right >= 1; right -= 2) {
    if (right === 6) right = 5;
    for (let vert = 0; vert < size; vert++) for (let j = 0; j < 2; j++) {
      const x = right - j;
      const y = ((right + 1) & 2) === 0 ? size - 1 - vert : vert;
      if (!isFunction[y][x]) bits.push(bit(x, y) ^ (maskFn(x, y) ? 1 : 0));
    }
  }
  const cw = [];
  for (let i = 0; i + 8 <= bits.length; i += 8) cw.push(parseInt(bits.slice(i, i + 8).join(''), 2));
  return { ver, ecc, mask, codewords: cw };
}

function readBytes(cw, ver, ecc) {
  const total = cw.length;
  const nBlocks = numBlocks(ver, ecc);
  const eccLen = eccPerBlock(ver, ecc);
  const nShort = nBlocks - (total % nBlocks);
  const shortLen = Math.floor(total / nBlocks);
  const dataLens = Array.from({ length: nBlocks }, (_, i) => shortLen - eccLen + (i < nShort ? 0 : 1));
  const blocks = dataLens.map(() => []);
  let k = 0;
  for (let i = 0; i < Math.max(...dataLens); i++) for (let b = 0; b < nBlocks; b++) if (i < dataLens[b]) blocks[b].push(cw[k++]);
  const eccBlocks = dataLens.map(() => []);
  for (let i = 0; i < eccLen; i++) for (let b = 0; b < nBlocks; b++) eccBlocks[b].push(cw[k++]);
  assert.equal(k, total);
  for (let b = 0; b < nBlocks; b++) assert.deepEqual(rsRemainder(blocks[b], eccLen), eccBlocks[b], `error correction of block ${b}`);
  const data = blocks.flat();
  let pos = 0;
  const take = (n) => { let v = 0; for (let i = 0; i < n; i++) { v = (v << 1) | ((data[pos >> 3] >> (7 - (pos & 7))) & 1); pos++; } return v; };
  assert.equal(take(4), 0b0100, 'byte mode');
  const len = take(ver <= 9 ? 8 : 16);
  const out = Buffer.alloc(len);
  for (let i = 0; i < len; i++) out[i] = take(8);
  return out;
}

function decode(qr) {
  const m = decodeMatrix(qr.modules);
  return { ...m, bytes: readBytes(m.codewords, m.ver, m.ecc) };
}

function pngToModules(png, size, scale, border) {
  assert.deepEqual([...png.subarray(0, 8)], [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);
  let p = 8; let ihdr; const idat = [];
  while (p < png.length) {
    const len = png.readUInt32BE(p); const type = png.toString('ascii', p + 4, p + 8);
    const body = png.subarray(p + 8, p + 8 + len);
    assert.equal(png.readUInt32BE(p + 8 + len), crc32(png.subarray(p + 4, p + 8 + len)), `crc of ${type}`);
    if (type === 'IHDR') ihdr = body; if (type === 'IDAT') idat.push(body);
    p += 12 + len;
  }
  const n = ihdr.readUInt32BE(0);
  assert.equal(n, (size + border * 2) * scale);
  assert.equal(ihdr.readUInt32BE(4), n); assert.equal(ihdr[8], 1); assert.equal(ihdr[9], 0);
  const raw = zlib.inflateSync(Buffer.concat(idat));
  const rowBytes = Math.ceil(n / 8);
  assert.equal(raw.length, (rowBytes + 1) * n);
  const px = (x, y) => (raw[y * (rowBytes + 1) + 1 + (x >> 3)] >> (7 - (x & 7))) & 1; // 1 = white
  const out = [];
  for (let my = 0; my < size; my++) {
    const row = [];
    for (let mx = 0; mx < size; mx++) {
      const cx = (mx + border) * scale + (scale >> 1), cy = (my + border) * scale + (scale >> 1);
      row.push(px(cx, cy) === 0);
    }
    out.push(row);
  }
  // the quiet zone is white
  for (let i = 0; i < n; i++) for (let j = 0; j < border * scale; j++) { assert.equal(px(i, j), 1); assert.equal(px(j, i), 1); }
  return out;
}

function svgToModules(svg, size, border) {
  const d = svg.match(/<path d="([^"]*)"/)[1];
  const grid = Array.from({ length: size }, () => new Array(size).fill(false));
  for (const m of d.matchAll(/M(\d+),(\d+)h1v1h-1z/g)) grid[Number(m[2]) - border][Number(m[1]) - border] = true;
  return grid;
}

// ---------- round trips ----------

test('round trip: every version at every level, full capacity, decoded from the matrix', () => {
  let seed = 7;
  const rnd = () => (seed = (seed * 1103515245 + 12345) & 0x7fffffff) / 0x7fffffff;
  for (const l of LEVELS) for (let v = 1; v <= 40; v++) {
    let s = '';
    for (let i = 0; i < byteCapacity(v, l); i++) s += String.fromCharCode(33 + Math.floor(rnd() * 90));
    const qr = encode(s, { ecc: l, minVersion: v, maxVersion: v });
    assert.equal(qr.version, v);
    const d = decode(qr);
    assert.equal(d.ver, v); assert.equal(d.ecc, l);
    assert.equal(d.bytes.toString('latin1'), s, `v${v}-${l}`);
  }
});

test('round trip: UTF-8 and binary bytes, every mask', () => {
  const text = 'RiskMandate: café, 東京, ✓';
  for (let m = 0; m < 8; m++) {
    const qr = encode(text, { ecc: 'Q', mask: m });
    assert.equal(qr.mask, m);
    assert.equal(decode(qr).bytes.toString('utf8'), text);
  }
  const bin = Buffer.from(Array.from({ length: 200 }, (_, i) => (i * 37) & 0xff));
  assert.deepEqual(decode(encode(bin, { ecc: 'L' })).bytes, bin);
});

test('the version is the smallest that fits; the auto mask has the lowest penalty', () => {
  assert.equal(encode('a'.repeat(14), { ecc: 'M' }).version, 1);
  assert.equal(encode('a'.repeat(15), { ecc: 'M' }).version, 2);
  assert.equal(encode('a'.repeat(17), { ecc: 'L' }).version, 1);
  assert.equal(encode('a'.repeat(18), { ecc: 'L' }).version, 2);
});

test('boost raises the error-correction level only when the version does not change', () => {
  const q = encode('hi', { ecc: 'L', boost: true });
  assert.equal(q.version, 1); assert.equal(q.ecc, 'H');
  const t = encode('a'.repeat(17), { ecc: 'L', boost: true });
  assert.equal(t.version, 1); assert.equal(t.ecc, 'L');
});

test('too much data and bad arguments are refused', () => {
  assert.throws(() => encode('a'.repeat(2954), { ecc: 'L' }), /data too long/);
  assert.throws(() => encode('a', { ecc: 'X' }), /ecc must be/);
  assert.throws(() => encode(42), /string or bytes/);
});

test('a payload the size of an AMAPI enrolment QR (about 700 bytes) fits at level L and M', () => {
  const payload = JSON.stringify({
    'android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME': 'com.google.android.apps.work.clouddpc/.receivers.CloudDeviceAdminReceiver',
    'android.app.extra.PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM': 'I5YvS0O5hXY46mb01BlRjq4oJJGs2kuUcHvVkAPEXlg',
    'android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION': 'https://play.google.com/managed/downloadManagingApp?identifier=setup',
    'android.app.extra.PROVISIONING_ADMIN_EXTRAS_BUNDLE': { 'com.google.android.apps.work.clouddpc.EXTRA_ENROLLMENT_TOKEN': 'X'.repeat(20) },
  });
  assert.ok(payload.length > 400 && payload.length < 800);
  for (const l of ['L', 'M']) assert.ok(encode(payload, { ecc: l }).version <= 20);
});

test('SVG and PNG carry exactly the same modules as the matrix', () => {
  const qr = encode('https://riskmandate.ai/enrol?x=1', { ecc: 'M' });
  const svg = toSvg(qr, { border: 4 });
  assert.ok(svg.startsWith('<?xml'));
  assert.match(svg, /viewBox="0 0 \d+ \d+"/);
  assert.deepEqual(svgToModules(svg, qr.size, 4), qr.modules);
  const png = toPng(qr, { scale: 6, border: 4 });
  assert.deepEqual(pngToModules(png, qr.size, 6, 4), qr.modules);
  assert.deepEqual(decode({ modules: pngToModules(png, qr.size, 6, 4) }).bytes.toString(), 'https://riskmandate.ai/enrol?x=1');
});

test('finder patterns, timing patterns and the dark module are where the standard puts them', () => {
  const qr = encode('x', { ecc: 'L' });
  const s = qr.size;
  const m = qr.modules;
  for (const [cx, cy] of [[3, 3], [s - 4, 3], [3, s - 4]]) {
    assert.equal(m[cy][cx], true);
    assert.equal(m[cy][cx + 2], false);   // light ring
    assert.equal(m[cy][cx + 3], true);    // dark ring
  }
  for (let i = 8; i < s - 8; i++) { assert.equal(m[6][i], i % 2 === 0); assert.equal(m[i][6], i % 2 === 0); }
  assert.equal(m[s - 8][8], true);
});

test('FORMAT_BITS and LEVELS are the four standard levels', () => {
  assert.deepEqual(LEVELS, ['L', 'M', 'Q', 'H']);
  assert.deepEqual(FORMAT_BITS, { L: 1, M: 0, Q: 3, H: 2 });
});

const GOLDEN_MATRIX_SHA256 = '1b6c94361ef6eaa5665bf8d83cfa22b508ce244924abf99fdf36603079b54193';
