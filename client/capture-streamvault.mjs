// Captures StreamVault screenshots for the portfolio.
// Usage: node scripts/capture-screenshots.mjs "<output dir>"
// Needs the API (8080) and the client (3000) running, with at least one movie scanned.
import { chromium } from "playwright";
import { mkdirSync } from "node:fs";
import { join } from "node:path";

const WEB = process.env.WEB_URL ?? "http://localhost:3000";
const API = process.env.API_URL ?? "http://localhost:8080";
const OUT = process.argv[2] ?? "./screenshots";
const EMAIL = process.env.SV_EMAIL ?? "demo@streamvault.dev";
const PASSWORD = process.env.SV_PASSWORD ?? "demo-pass-123";
mkdirSync(OUT, { recursive: true });

const call = async (path, { method = "GET", token, body } = {}) => {
  const res = await fetch(API + path, {
    method,
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  return { ok: res.ok, status: res.status, data: text ? JSON.parse(text) : null };
};

// --- demo data through the API
await call("/api/auth/register", { method: "POST", body: { email: EMAIL, password: PASSWORD } });
const login = await call("/api/auth/login", { method: "POST", body: { email: EMAIL, password: PASSWORD } });
if (!login.ok) throw new Error(`Login failed (${login.status}). Is the API running?`);
const token = login.data.token;

let profiles = (await call("/api/profiles", { token })).data ?? [];
for (const name of ["Siddhant", "Guest"]) {
  if (profiles.length >= 2) break;
  const created = await call("/api/profiles", { method: "POST", token, body: { name, ageRestriction: 18 } });
  if (created.ok) profiles.push(created.data);
}
const profile = profiles[0];

const list = (await call("/api/movies?page=0&size=20&sortBy=title&direction=asc", { token })).data;
const movies = list?.content ?? [];
if (!movies.length) throw new Error("No movies found. Check app.media.root-path and the startup scan.");
const movie = movies[0];

const settle = async (page, ms = 1500) => {
  await page.waitForLoadState("networkidle").catch(() => {});
  await page.waitForFunction(() => [...document.images].every((i) => i.complete), null, { timeout: 8000 }).catch(() => {});
  await page.waitForTimeout(ms);
};

const browser = await chromium.launch();

async function session({ viewport, mobile, authed }, fn) {
  const ctx = await browser.newContext({
    viewport,
    deviceScaleFactor: 2,
    isMobile: mobile,
    hasTouch: mobile,
    reducedMotion: "reduce",
  });
  if (authed) {
    await ctx.addInitScript(([t, p]) => {
      localStorage.setItem("sv_token", t);
      localStorage.setItem("sv_profile", JSON.stringify(p));
    }, [token, profile]);
  }
  const page = await ctx.newPage();
  try { await fn(page); } finally { await ctx.close(); }
}

const shot = async (page, name, opts = {}) => {
  try {
    await page.screenshot({ path: join(OUT, name), ...opts });
    console.log("saved", name);
  } catch (e) { console.warn("failed", name, e.message); }
};

const desktop = { width: 1440, height: 900 };
const phone = { width: 390, height: 844 };

// signed out
await session({ viewport: desktop, authed: false }, async (page) => {
  await page.goto(`${WEB}/login`);
  await settle(page, 800);
  await shot(page, "streamvault-login.png");
  await page.getByRole("button", { name: /New to StreamVault/i }).click();
  await page.waitForTimeout(400);
  await shot(page, "streamvault-register.png");
});

// signed in (profile picker needs the token only)
await session({ viewport: desktop, authed: true }, async (page) => {
  await page.goto(`${WEB}/profiles`);
  await settle(page, 800);
  await shot(page, "streamvault-profiles.png");

  await page.goto(`${WEB}/home`);
  await settle(page, 2500);
  await shot(page, "streamvault-home.png");
  await shot(page, "streamvault-home-full.png", { fullPage: true });

  await page.goto(`${WEB}/movie/${movie.id}`);
  await settle(page, 2000);
  await shot(page, "streamvault-movie.png");

  await page.goto(`${WEB}/player/${movie.id}`);
  await settle(page, 7000);
  await page.mouse.move(700, 600);
  await page.waitForTimeout(500);
  await shot(page, "streamvault-player.png");
});

// mobile
await session({ viewport: phone, mobile: true, authed: true }, async (page) => {
  await page.goto(`${WEB}/home`);
  await settle(page, 2500);
  await shot(page, "streamvault-mobile-home.png");
  await page.goto(`${WEB}/movie/${movie.id}`);
  await settle(page, 2000);
  await shot(page, "streamvault-mobile-movie.png");
});

await browser.close();
console.log("Done ->", OUT);
