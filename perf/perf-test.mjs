// StreamVault performance baseline. Node 18+, no dependencies.
//   node perf-test.mjs run <label>            -> results/<label>.json
//   node perf-test.mjs compare <a> <b>        -> side-by-side table
// Env: SV_BASE (http://localhost:8080) SV_EMAIL SV_PASSWORD SV_MOVIE (movie id, default: first movie)
//      SV_INVITE_CODE (needed to create the perf account once other accounts exist)
//      SV_CONC (20) SV_REQS (200) SV_WARMUP_SEC (20)
import { writeFileSync, readFileSync, mkdirSync } from 'node:fs';

const BASE = process.env.SV_BASE || 'http://localhost:8080';
const EMAIL = process.env.SV_EMAIL || 'perf@test.local';
const PASS = process.env.SV_PASSWORD || 'Perf@12345';
const CONC = +(process.env.SV_CONC || 20);
const REQS = +(process.env.SV_REQS || 200);
const WARM = +(process.env.SV_WARMUP_SEC || 20);

const now = () => performance.now();
const pct = (a, p) => { if (!a.length) return null; const s = [...a].sort((x, y) => x - y); return +s[Math.min(s.length - 1, Math.ceil(p / 100 * s.length) - 1)].toFixed(1); };
const stats = (a) => ({ n: a.length, p50: pct(a, 50), p95: pct(a, 95), p99: pct(a, 99), max: a.length ? +Math.max(...a).toFixed(1) : null });
const sleep = (ms) => new Promise(r => setTimeout(r, ms));

async function req(path, token, opts = {}) {
  const t = now();
  const res = await fetch(BASE + path, { ...opts, headers: { ...(token ? { Authorization: `Bearer ${token}` } : {}), ...(opts.headers || {}) } });
  const buf = await res.arrayBuffer();
  return { ms: now() - t, status: res.status, bytes: buf.byteLength, text: () => new TextDecoder().decode(buf) };
}

async function login() {
  const body = JSON.stringify({ email: EMAIL, password: PASS });
  const h = { 'Content-Type': 'application/json' };
  let r = await req('/api/auth/login', null, { method: 'POST', body, headers: h });
  if (r.status !== 200) {
    const regBody = JSON.stringify({ email: EMAIL, password: PASS, inviteCode: process.env.SV_INVITE_CODE });
    await req('/api/auth/register', null, { method: 'POST', body: regBody, headers: h });
    r = await req('/api/auth/login', null, { method: 'POST', body, headers: h });
  }
  if (r.status >= 300) throw new Error(`auth failed (${r.status}): ${r.text()}`);
  return JSON.parse(r.text()).token;
}

async function load(name, path, token) {
  const lat = [], errs = { n: 0 }; let next = 0;
  const t0 = now();
  await Promise.all(Array.from({ length: CONC }, async () => {
    while (next++ < REQS) { const r = await req(path, token); r.status === 200 ? lat.push(r.ms) : errs.n++; }
  }));
  const wall = (now() - t0) / 1000;
  return { name, ...stats(lat), rps: +(lat.length / wall).toFixed(1), errors: errs.n };
}

async function apiSuite(token) {
  const out = [];
  await req('/api/movies?page=0&size=24', token); // warm
  const seq = [];
  for (let i = 0; i < 30; i++) seq.push((await req('/api/movies?page=0&size=24', token)).ms);
  out.push({ name: 'GET /movies (sequential)', ...stats(seq) });
  out.push(await load(`GET /movies page (conc ${CONC})`, '/api/movies?page=0&size=24&sort=title,asc', token));
  out.push(await load(`GET /movies genre filter (conc ${CONC})`, '/api/movies?page=0&size=24&genre=Drama', token));
  out.push(await load(`GET /movies search (conc ${CONC})`, '/api/movies?page=0&size=24&search=a', token));
  out.push(await load(`GET /movies/filters (conc ${CONC})`, '/api/movies/filters', token));
  return out;
}

function parsePlaylist(txt) {
  const segs = []; const L = txt.split('\n');
  for (let i = 0; i < L.length; i++) if (L[i].startsWith('#EXTINF:')) segs.push({ dur: parseFloat(L[i].slice(8)), file: L[i + 1].trim() });
  let t = 0; segs.forEach(s => { s.start = t; t += s.dur; });
  return { segs, total: t, ended: txt.includes('#EXT-X-ENDLIST') };
}

async function streamSuite(token, movieId) {
  const r = {};
  await req(`/api/stream/${movieId}/session`, token, { method: 'DELETE' }).catch(() => {});
  await sleep(1500);
  const pl = `/api/stream/${movieId}/playlist.m3u8`; // token goes in the Authorization header
  const t0 = now();
  let p = await req(pl, token);
  r.coldPlaylistMs = +(now() - t0).toFixed(0); r.playlistStatus = p.status;
  if (p.status !== 200) return { ...r, error: p.text().slice(0, 200) };
  let pls = parsePlaylist(p.text());
  const s0 = await req(`/api/stream/${movieId}/segments/${pls.segs[0].file}`, token);
  r.timeToFirstFrameMs = +(now() - t0).toFixed(0);
  const durs = pls.segs.map(s => s.dur);
  r.firstSegmentMs = +s0.ms.toFixed(0);

  await sleep(WARM * 1000);
  p = await req(pl, token); const pl2 = parsePlaylist(p.text());
  r.encodedSecondsAfterWarmup = +pl2.total.toFixed(1);
  r.encodeSpeedX = +((pl2.total - pls.total) / WARM).toFixed(2); // video-seconds produced per wall-second
  r.segmentCount = pl2.segs.length;
  const d = pl2.segs.map(s => s.dur);
  r.segmentSec = { avg: +(d.reduce((a, b) => a + b, 0) / d.length).toFixed(2), max: +Math.max(...d).toFixed(2), min: +Math.min(...d).toFixed(2) };

  const dl = [];
  for (const s of pl2.segs.slice(0, 6)) { const x = await req(`/api/stream/${movieId}/segments/${s.file}`, token); dl.push(x.ms); }
  r.segmentFetchMs = stats(dl);

  r.seeks = [];
  for (const f of [0.25, 0.5, 0.75, 0.95]) {
    const target = pl2.total * f;
    const seg = pl2.segs.find(s => target >= s.start && target < s.start + s.dur) || pl2.segs.at(-1);
    const x = await req(`/api/stream/${movieId}/segments/${seg.file}`, token);
    r.seeks.push({ targetSec: +target.toFixed(1), segmentSec: +seg.dur.toFixed(1), discardedSec: +(target - seg.start).toFixed(1), fetchMs: +x.ms.toFixed(0), mbit: +(x.bytes * 8 / 1e6).toFixed(1) });
  }
  r.avgDiscardedSec = +(r.seeks.reduce((a, s) => a + s.discardedSec, 0) / r.seeks.length).toFixed(1);
  await req(`/api/stream/${movieId}/session`, token, { method: 'DELETE' }).catch(() => {});
  return r;
}

async function run(label) {
  const token = await login();
  const list = JSON.parse((await req('/api/movies?page=0&size=1', token)).text());
  const movieId = process.env.SV_MOVIE || (list.content ?? list)[0]?.id;
  console.log(`Base ${BASE} | movie ${movieId} | conc ${CONC} x ${REQS}`);
  const result = { label, at: new Date().toISOString(), api: await apiSuite(token), stream: movieId ? await streamSuite(token, movieId) : null };
  mkdirSync('results', { recursive: true });
  writeFileSync(`results/${label}.json`, JSON.stringify(result, null, 2));
  console.table(result.api);
  console.log(JSON.stringify(result.stream, null, 2));
  console.log(`Saved results/${label}.json`);
}

function compare(a, b) {
  const A = JSON.parse(readFileSync(`results/${a}.json`)), B = JSON.parse(readFileSync(`results/${b}.json`));
  const d = (x, y, lowerBetter = true) => (x == null || y == null) ? '' : `${(((y - x) / x) * 100).toFixed(0)}% ${((y < x) === lowerBetter) ? 'better' : 'worse'}`;
  const rows = [];
  A.api.forEach((x, i) => { const y = B.api[i]; if (!y) return; rows.push({ metric: x.name + ' p95 ms', [a]: x.p95, [b]: y.p95, change: d(x.p95, y.p95) }); if (x.rps) rows.push({ metric: x.name + ' rps', [a]: x.rps, [b]: y.rps, change: d(x.rps, y.rps, false) }); });
  const s = A.stream, t = B.stream;
  if (s && t) {
    const add = (m, x, y, lb = true) => rows.push({ metric: m, [a]: x, [b]: y, change: d(x, y, lb) });
    add('time to first frame ms', s.timeToFirstFrameMs, t.timeToFirstFrameMs);
    add('segment max sec', s.segmentSec?.max, t.segmentSec?.max);
    add('avg discarded sec on seek', s.avgDiscardedSec, t.avgDiscardedSec);
    add('encode speed (x realtime)', s.encodeSpeedX, t.encodeSpeedX, false);
    add('segment fetch p95 ms', s.segmentFetchMs?.p95, t.segmentFetchMs?.p95);
  }
  console.table(rows);
}

const [cmd, a, b] = process.argv.slice(2);
if (cmd === 'run') run(a || 'baseline').catch(e => { console.error(e.message); process.exit(1); });
else if (cmd === 'compare') compare(a, b);
else console.log('usage: node perf-test.mjs run <label> | compare <labelA> <labelB>');
