// qr.mjs: a small QR Code (Model 2) encoder with no dependencies. Byte mode, versions 1 to 40,
// error correction L, M, Q, H, automatic mask choice. Writes SVG and PNG. Node 18+.
// Follows ISO/IEC 18004; tested against known vectors and by decoding its own output
// (devices/test/qr.test.mjs). Used by amapi.mjs so `enrol` can write the QR image itself.

import zlib from 'node:zlib';

// ---------- tables ----------

// Index by ECC level then version (index 0 unused).
const ECC_PER_BLOCK = {
  L: [-1, 7, 10, 15, 20, 26, 18, 20, 24, 30, 18, 20, 24, 26, 30, 22, 24, 28, 30, 28, 28, 28, 28, 30, 30, 26, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30],
  M: [-1, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26, 30, 22, 22, 24, 24, 28, 28, 26, 26, 26, 26, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28],
  Q: [-1, 13, 22, 18, 26, 18, 24, 18, 22, 20, 24, 28, 26, 24, 20, 30, 24, 28, 28, 26, 30, 28, 30, 30, 30, 30, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30],
  H: [-1, 17, 28, 22, 16, 22, 28, 26, 26, 24, 28, 24, 28, 22, 24, 24, 30, 28, 28, 26, 28, 30, 24, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30],
};
const NUM_BLOCKS = {
  L: [-1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 4, 4, 4, 4, 4, 6, 6, 6, 6, 7, 8, 8, 9, 9, 10, 12, 12, 12, 13, 14, 15, 16, 17, 18, 19, 19, 20, 21, 22, 24, 25],
  M: [-1, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5, 5, 8, 9, 9, 10, 10, 11, 13, 14, 16, 17, 17, 18, 20, 21, 23, 25, 26, 28, 29, 31, 33, 35, 37, 38, 40, 43, 45, 47, 49],
  Q: [-1, 1, 1, 2, 2, 4, 4, 6, 6, 8, 8, 8, 10, 12, 16, 12, 17, 16, 18, 21, 20, 23, 23, 25, 27, 29, 34, 34, 35, 38, 40, 43, 45, 48, 51, 53, 56, 59, 62, 65, 68],
  H: [-1, 1, 1, 2, 4, 4, 4, 5, 6, 8, 8, 11, 11, 16, 16, 18, 16, 19, 21, 25, 25, 25, 34, 30, 32, 35, 37, 40, 42, 45, 48, 51, 54, 57, 60, 63, 66, 70, 74, 77, 81],
};
export const FORMAT_BITS = { L: 1, M: 0, Q: 3, H: 2 };
export const LEVELS = ['L', 'M', 'Q', 'H'];

export const eccPerBlock = (ver, ecc) => ECC_PER_BLOCK[ecc][ver];
export const numBlocks = (ver, ecc) => NUM_BLOCKS[ecc][ver];

export function sizeOf(version) { return version * 4 + 17; }

// Number of data modules (bits, including remainder bits) for a version, after function patterns.
export function rawDataModules(ver) {
  let r = (16 * ver + 128) * ver + 64;
  if (ver >= 2) {
    const n = Math.floor(ver / 7) + 2;
    r -= (25 * n - 10) * n - 55;
    if (ver >= 7) r -= 36;
  }
  return r;
}

export function dataCodewords(ver, ecc) {
  return Math.floor(rawDataModules(ver) / 8) - ECC_PER_BLOCK[ecc][ver] * NUM_BLOCKS[ecc][ver];
}

// Maximum bytes in byte mode.
export function byteCapacity(ver, ecc) {
  const countBits = ver <= 9 ? 8 : 16;
  return Math.floor((dataCodewords(ver, ecc) * 8 - 4 - countBits) / 8);
}

export function alignmentPositions(ver) {
  if (ver === 1) return [];
  const n = Math.floor(ver / 7) + 2;
  const step = ver === 32 ? 26 : Math.ceil((ver * 4 + 4) / (n * 2 - 2)) * 2;
  const out = [6];
  for (let pos = sizeOf(ver) - 7; out.length < n; pos -= step) out.splice(1, 0, pos);
  return out;
}

// ---------- Reed-Solomon over GF(256), polynomial 0x11D ----------

const EXP = new Uint8Array(512);
const LOG = new Uint8Array(256);
{
  let x = 1;
  for (let i = 0; i < 255; i++) { EXP[i] = x; LOG[x] = i; x <<= 1; if (x & 0x100) x ^= 0x11d; }
  for (let i = 255; i < 512; i++) EXP[i] = EXP[i - 255];
}
function gfMul(a, b) { return a === 0 || b === 0 ? 0 : EXP[LOG[a] + LOG[b]]; }

export function rsGenerator(degree) {
  let poly = [1];
  for (let i = 0; i < degree; i++) {
    const next = new Array(poly.length + 1).fill(0);
    for (let j = 0; j < poly.length; j++) {
      next[j] ^= poly[j];
      next[j + 1] ^= gfMul(poly[j], EXP[i]);
    }
    poly = next;
  }
  return poly; // highest degree first, leading 1
}

// Error-correction codewords for one block of data codewords.
export function rsRemainder(data, degree) {
  const gen = rsGenerator(degree);
  const rem = new Array(degree).fill(0);
  for (const b of data) {
    const factor = b ^ rem.shift();
    rem.push(0);
    for (let i = 0; i < degree; i++) rem[i] ^= gfMul(gen[i + 1], factor);
  }
  return rem;
}

// ---------- encoding ----------

function toBytes(input) {
  if (typeof input === 'string') return Buffer.from(input, 'utf8');
  if (input instanceof Uint8Array) return Buffer.from(input);
  throw new TypeError('QR input must be a string or bytes');
}

function buildDataCodewords(bytes, ver, ecc) {
  const bits = [];
  const put = (value, n) => { for (let i = n - 1; i >= 0; i--) bits.push((value >>> i) & 1); };
  put(0b0100, 4);
  put(bytes.length, ver <= 9 ? 8 : 16);
  for (const b of bytes) put(b, 8);
  const cap = dataCodewords(ver, ecc) * 8;
  put(0, Math.min(4, cap - bits.length));
  while (bits.length % 8) bits.push(0);
  const out = [];
  for (let i = 0; i < bits.length; i += 8) {
    let v = 0;
    for (let j = 0; j < 8; j++) v = (v << 1) | bits[i + j];
    out.push(v);
  }
  for (let pad = 0xec; out.length < cap / 8; pad ^= 0xec ^ 0x11) out.push(pad);
  return out;
}

// Splits into blocks, appends error correction, interleaves.
export function interleave(data, ver, ecc) {
  const nBlocks = NUM_BLOCKS[ecc][ver];
  const eccLen = ECC_PER_BLOCK[ecc][ver];
  const raw = Math.floor(rawDataModules(ver) / 8);
  const nShort = nBlocks - (raw % nBlocks);
  const shortLen = Math.floor(raw / nBlocks);
  const blocks = [];
  for (let i = 0, k = 0; i < nBlocks; i++) {
    const len = shortLen - eccLen + (i < nShort ? 0 : 1);
    const d = data.slice(k, k + len);
    k += len;
    blocks.push({ data: d, ecc: rsRemainder(d, eccLen) });
  }
  const out = [];
  const maxData = shortLen - eccLen + 1;
  for (let i = 0; i < maxData; i++) for (const b of blocks) if (i < b.data.length) out.push(b.data[i]);
  for (let i = 0; i < eccLen; i++) for (const b of blocks) out.push(b.ecc[i]);
  return out;
}

// ---------- matrix ----------

const MASKS = [
  (x, y) => (x + y) % 2 === 0,
  (x, y) => y % 2 === 0,
  (x, y) => x % 3 === 0,
  (x, y) => (x + y) % 3 === 0,
  (x, y) => (Math.floor(x / 3) + Math.floor(y / 2)) % 2 === 0,
  (x, y) => ((x * y) % 2) + ((x * y) % 3) === 0,
  (x, y) => (((x * y) % 2) + ((x * y) % 3)) % 2 === 0,
  (x, y) => (((x + y) % 2) + ((x * y) % 3)) % 2 === 0,
];

export function formatBits(ecc, mask) {
  const data = (FORMAT_BITS[ecc] << 3) | mask;
  let rem = data;
  for (let i = 0; i < 10; i++) rem = (rem << 1) ^ ((rem >>> 9) * 0x537);
  return ((data << 10) | rem) ^ 0x5412;
}

export function versionBits(ver) {
  let rem = ver;
  for (let i = 0; i < 12; i++) rem = (rem << 1) ^ ((rem >>> 11) * 0x1f25);
  return (ver << 12) | rem;
}

function newGrid(size) { return Array.from({ length: size }, () => new Array(size).fill(false)); }

// Returns { modules, isFunction } with every function pattern drawn (format bits as placeholder).
export function drawFunctionPatterns(ver) {
  const size = sizeOf(ver);
  const modules = newGrid(size);
  const isFunction = newGrid(size);
  const set = (x, y, dark) => { modules[y][x] = dark; isFunction[y][x] = true; };

  for (let i = 0; i < size; i++) { set(6, i, i % 2 === 0); set(i, 6, i % 2 === 0); }
  const finder = (cx, cy) => {
    for (let dy = -4; dy <= 4; dy++) for (let dx = -4; dx <= 4; dx++) {
      const x = cx + dx, y = cy + dy;
      if (x < 0 || y < 0 || x >= size || y >= size) continue;
      const d = Math.max(Math.abs(dx), Math.abs(dy));
      set(x, y, d !== 2 && d !== 4);
    }
  };
  finder(3, 3); finder(size - 4, 3); finder(3, size - 4);
  const al = alignmentPositions(ver);
  for (let i = 0; i < al.length; i++) for (let j = 0; j < al.length; j++) {
    if ((i === 0 && j === 0) || (i === 0 && j === al.length - 1) || (i === al.length - 1 && j === 0)) continue;
    for (let dy = -2; dy <= 2; dy++) for (let dx = -2; dx <= 2; dx++) set(al[i] + dx, al[j] + dy, Math.max(Math.abs(dx), Math.abs(dy)) !== 1);
  }
  drawFormat(modules, isFunction, 'L', 0);
  if (ver >= 7) {
    const bits = versionBits(ver);
    for (let i = 0; i < 18; i++) {
      const dark = ((bits >>> i) & 1) !== 0;
      const a = size - 11 + (i % 3), b = Math.floor(i / 3);
      set(a, b, dark); set(b, a, dark);
    }
  }
  return { modules, isFunction };
}

function drawFormat(modules, isFunction, ecc, mask) {
  const size = modules.length;
  const bits = formatBits(ecc, mask);
  const set = (x, y, dark) => { modules[y][x] = dark; isFunction[y][x] = true; };
  const bit = (i) => ((bits >>> i) & 1) !== 0;
  for (let i = 0; i <= 5; i++) set(8, i, bit(i));
  set(8, 7, bit(6)); set(8, 8, bit(7)); set(7, 8, bit(8));
  for (let i = 9; i < 15; i++) set(14 - i, 8, bit(i));
  for (let i = 0; i < 8; i++) set(size - 1 - i, 8, bit(i));
  for (let i = 8; i < 15; i++) set(8, size - 15 + i, bit(i));
  set(8, size - 8, true); // the always-dark module
}

function drawCodewords(modules, isFunction, codewords) {
  const size = modules.length;
  let i = 0;
  for (let right = size - 1; right >= 1; right -= 2) {
    if (right === 6) right = 5;
    for (let vert = 0; vert < size; vert++) {
      for (let j = 0; j < 2; j++) {
        const x = right - j;
        const upward = ((right + 1) & 2) === 0;
        const y = upward ? size - 1 - vert : vert;
        if (!isFunction[y][x] && i < codewords.length * 8) {
          modules[y][x] = ((codewords[i >>> 3] >>> (7 - (i & 7))) & 1) !== 0;
          i++;
        }
      }
    }
  }
  if (i !== codewords.length * 8) throw new Error('internal error: codeword placement');
}

function applyMask(modules, isFunction, mask) {
  const size = modules.length;
  for (let y = 0; y < size; y++) for (let x = 0; x < size; x++) {
    if (!isFunction[y][x] && MASKS[mask](x, y)) modules[y][x] = !modules[y][x];
  }
}

export function penalty(modules) {
  const size = modules.length;
  let score = 0;
  const line = (get) => {
    for (let a = 0; a < size; a++) {
      let run = 1;
      let bits = 0; // last 11 modules
      for (let b = 0; b < size; b++) {
        const v = get(a, b);
        if (b > 0) {
          if (v === get(a, b - 1)) { run++; } else { if (run >= 5) score += 3 + run - 5; run = 1; }
        }
        bits = ((bits << 1) | (v ? 1 : 0)) & 0x7ff;
        if (b >= 10 && (bits === 0b10111010000 || bits === 0b00001011101)) score += 40;
      }
      if (run >= 5) score += 3 + run - 5;
    }
  };
  line((r, c) => modules[r][c]);
  line((c, r) => modules[r][c]);
  for (let y = 0; y < size - 1; y++) for (let x = 0; x < size - 1; x++) {
    const v = modules[y][x];
    if (v === modules[y][x + 1] && v === modules[y + 1][x] && v === modules[y + 1][x + 1]) score += 3;
  }
  let dark = 0;
  for (const row of modules) for (const v of row) if (v) dark++;
  const total = size * size;
  score += (Math.ceil(Math.abs(dark * 20 - total * 10) / total) - 1) * 10;
  return score;
}

// encode(input, { ecc: 'M', minVersion: 1, maxVersion: 40, mask: undefined, boost: false })
// Returns { version, size, ecc, mask, modules } where modules[y][x] is true for a dark module.
export function encode(input, opts = {}) {
  const { ecc: eccIn = 'M', minVersion = 1, maxVersion = 40, mask: forcedMask, boost = false } = opts;
  if (!LEVELS.includes(eccIn)) throw new RangeError(`ecc must be one of ${LEVELS.join(', ')}`);
  const bytes = toBytes(input);
  let ver = minVersion;
  for (; ; ver++) {
    if (ver > maxVersion) throw new RangeError(`data too long: ${bytes.length} bytes does not fit version ${maxVersion} at level ${eccIn} (max ${byteCapacity(maxVersion, eccIn)})`);
    if (bytes.length <= byteCapacity(ver, eccIn)) break;
  }
  let ecc = eccIn;
  if (boost) for (const l of ['M', 'Q', 'H']) if (LEVELS.indexOf(l) > LEVELS.indexOf(ecc) && bytes.length <= byteCapacity(ver, l)) ecc = l;
  const codewords = interleave(buildDataCodewords(bytes, ver, ecc), ver, ecc);
  const { modules, isFunction } = drawFunctionPatterns(ver);
  drawCodewords(modules, isFunction, codewords);
  let best = forcedMask;
  if (best === undefined) {
    let bestScore = Infinity;
    for (let m = 0; m < 8; m++) {
      applyMask(modules, isFunction, m);
      drawFormat(modules, isFunction, ecc, m);
      const s = penalty(modules);
      if (s < bestScore) { bestScore = s; best = m; }
      applyMask(modules, isFunction, m); // undo
    }
  }
  applyMask(modules, isFunction, best);
  drawFormat(modules, isFunction, ecc, best);
  return { version: ver, size: sizeOf(ver), ecc, mask: best, modules };
}

// ---------- output ----------

// SVG with one path (crisp at any size), a white quiet zone of `border` modules (spec minimum 4).
export function toSvg(qr, { border = 4, dark = '#000000', light = '#ffffff' } = {}) {
  const n = qr.size + border * 2;
  const parts = [];
  for (let y = 0; y < qr.size; y++) for (let x = 0; x < qr.size; x++) {
    if (qr.modules[y][x]) parts.push(`M${x + border},${y + border}h1v1h-1z`);
  }
  return `<?xml version="1.0" encoding="UTF-8"?>\n<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${n} ${n}" width="${n * 8}" height="${n * 8}" shape-rendering="crispEdges">\n<rect width="${n}" height="${n}" fill="${light}"/>\n<path d="${parts.join('')}" fill="${dark}"/>\n</svg>\n`;
}

const CRC_TABLE = (() => {
  const t = new Uint32Array(256);
  for (let n = 0; n < 256; n++) { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; t[n] = c >>> 0; }
  return t;
})();
export function crc32(buf) {
  let c = 0xffffffff;
  for (const b of buf) c = CRC_TABLE[(c ^ b) & 0xff] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
}

function chunk(type, data) {
  const len = Buffer.alloc(4); len.writeUInt32BE(data.length);
  const td = Buffer.concat([Buffer.from(type, 'ascii'), data]);
  const crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(td));
  return Buffer.concat([len, td, crc]);
}

// 1-bit greyscale PNG: `scale` pixels per module, `border` modules of quiet zone.
export function toPng(qr, { scale = 8, border = 4 } = {}) {
  const n = (qr.size + border * 2) * scale;
  const rowBytes = Math.ceil(n / 8);
  const raw = Buffer.alloc((rowBytes + 1) * n); // filter byte 0 per row; bits 1 = white
  for (let py = 0; py < n; py++) {
    const base = py * (rowBytes + 1);
    const my = Math.floor(py / scale) - border;
    for (let px = 0; px < n; px++) {
      const mx = Math.floor(px / scale) - border;
      const isDark = my >= 0 && mx >= 0 && my < qr.size && mx < qr.size && qr.modules[my][mx];
      if (!isDark) raw[base + 1 + (px >> 3)] |= 0x80 >> (px & 7);
    }
  }
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(n, 0); ihdr.writeUInt32BE(n, 4);
  ihdr[8] = 1; ihdr[9] = 0; ihdr[10] = 0; ihdr[11] = 0; ihdr[12] = 0; // 1-bit, greyscale
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', ihdr), chunk('IDAT', zlib.deflateSync(raw, { level: 9 })), chunk('IEND', Buffer.alloc(0)),
  ]);
}
