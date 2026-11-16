# StreamVault

A self-hosted movie streaming platform. Upload your own media library and stream it beautifully from any browser.

**Stack:** Spring Boot 3 · H2 (embedded) · Next.js 15 · HLS.js · FFmpeg · TMDB API

---

## Prerequisites

- Java 21+
- Node.js 18+
- FFmpeg ([download](https://ffmpeg.org/download.html))
- TMDB API key (free at [themoviedb.org](https://www.themoviedb.org/settings/api))

---

## Backend Setup

### 1. Set your secrets and paths

Secrets never go in `server/application.yml`, because that file is tracked by Git. Either set environment variables:

| Variable | What it is |
| --- | --- |
| `SV_JWT_SECRET` | Required. Output of `openssl rand -base64 32` |
| `SV_MEDIA_ROOT` | Folder containing your movie subfolders |
| `SV_FFMPEG_PATH` | `ffmpeg` if it is on PATH, else the full path, e.g. `C:\ffmpeg\bin\ffmpeg.exe` |
| `SV_TMDB_API_KEY` | Optional. Free at [themoviedb.org](https://www.themoviedb.org/settings/api) |
| `SV_DB_PASSWORD` | Optional. See "Database password" below |
| `SV_INVITE_CODE` | Needed for anyone to register after the first account. See "Accounts and invite codes" |
| `SV_CORS_ORIGINS` | Web app address(es), default `http://localhost:3000` |
| `SV_MAX_TRANSCODES` | Movies FFmpeg may convert at once, default 3 |
| `SV_CERT_COUNTRY` | Whose age ratings to use for profile limits, default `US` |
| `SV_ADMIN_EMAILS` | Accounts to make administrators at startup, comma-separated |

or copy `server/application-local.example.yml` to `server/application-local.yml` and fill it in. That file is ignored by Git. Environment variables take priority over it.

The server refuses to start if the JWT secret is missing or shorter than 32 bytes.

### 2. Media folder structure

StreamVault scans subfolders — each subfolder is a movie:

```
/movies
  /Inception (2010)
      Inception.mkv
  /The Dark Knight (2008)
      The.Dark.Knight.2008.mkv
```

### 3. Run the backend

```bash
cd server
./mvnw spring-boot:run        # Mac/Linux
mvnw.cmd spring-boot:run      # Windows
```

Server starts at `http://localhost:8080`

---

## Frontend Setup

### 1. Configure environment

```bash
cd client
cp .env.example .env.local
```

Edit `.env.local` if your backend runs elsewhere (see "HTTPS on your network" for the proxy setup):

```
NEXT_PUBLIC_API_URL=http://localhost:8080
```

### 2. Install and run

```bash
npm install
npm run dev
```

App starts at `http://localhost:3000`

---

## First Run

1. Open `http://localhost:3000`
2. Register an account
3. Create a profile
4. StreamVault auto-scans your media folder on startup — your movies will appear on the home page with TMDB metadata

---

## Accounts and invite codes

The first account on a new server can register freely. After that, registering needs an invite code: set `SV_INVITE_CODE` on the server and share the code with your household. With no code set, registration is closed.

Sign-in is limited to 5 wrong passwords per account in 15 minutes, and account creation to 5 per device per hour.

---

## Profiles, age limits and admins

- When you create a profile you choose who it is for (Adults, 16+, 13+, 7+, young kids). A limited profile only sees movies whose age rating is at or under its limit; unrated movies are hidden from it. Ratings come from TMDB for the country in `SV_CERT_COUNTRY` (default `US`; `IN` and `GB` are mapped too). Movies scanned before this feature get their rating on the next start, if a TMDB key is set.
- Age limits are a parental control: anyone who knows the account password can pick an adult profile.
- The first account registered on a new server is an administrator (it can trigger a re-scan). To make an existing account admin, set `SV_ADMIN_EMAILS=you@example.com` and restart.

---

## Signing in

The web app keeps your session in an HttpOnly cookie (`sv_session`), which page scripts cannot read, and signs out with `POST /api/auth/logout`. Scripts can still send the token from the login response as a `Bearer` header.

---

## HTTPS on your network

Out of the box the app uses plain HTTP, so on WiFi passwords and video can be read by anyone on the network. To serve everything over HTTPS:

1. Install [Caddy](https://caddyserver.com/download) on the server computer.
2. Start the API and web app so they only listen on this computer:
   - API: set `SERVER_ADDRESS=127.0.0.1` before `mvnw spring-boot:run`
   - Web app: `npm run dev -- -H 127.0.0.1` (or `npm run start -- -H 127.0.0.1`)
3. In `client/.env.local` set `NEXT_PUBLIC_API_URL=` (empty: same address as the web app).
4. Set `SV_HOST` to this computer's LAN IP or name (e.g. `192.168.1.20`), and `SV_CORS_ORIGINS` to `https://` plus the same value.
5. From the project folder run `caddy run`. Open `https://<SV_HOST>` on any device.
6. On each device, trust Caddy's root certificate once. Caddy prints where it saved it (`root.crt` under its data folder); install it as a trusted root on phones, TVs and other computers. Without this the browser shows a certificate warning.

---

## Database password

The H2 console is off by default. To protect the database file with a password:

1. Start once with the dev profile: `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`
2. Open `http://localhost:8080/h2-console` on the same computer and log in with JDBC URL `jdbc:h2:file:./data/streamvault;AUTO_SERVER=TRUE`, user `sa` and the current password (blank on an existing install).
3. Run `ALTER USER SA SET PASSWORD 'your-new-password';`
4. Stop the app, set the environment variable `SV_DB_PASSWORD=your-new-password`, and start it normally (without the dev profile).

---

## Notes

- **H2 database** stores data in `server/data/` — persists across restarts
- **HLS segments** are written to your system's temp folder and cleaned up automatically after 30 minutes of inactivity
- Stream copy is used for H.264 content (fast start), re-encode for other codecs
- Subtitle files (`.srt`, `.vtt`) placed in the movie folder are auto-detected