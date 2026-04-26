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

### 1. Configure `server/application.yml`

Set these values:

```yaml
app:
  media:
    root-path: /path/to/your/movies     # Folder containing your movie subfolders
    ffmpeg-path: ffmpeg                  # Full path if not on PATH, e.g. C:\ffmpeg\bin\ffmpeg.exe
  jwt:
    secret: CHANGE_ME                    # Run: openssl rand -base64 32
  tmdb:
    api-key: YOUR_TMDB_API_KEY
```

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

Edit `.env.local` if your backend runs on a different port:

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

## Notes

- **H2 database** stores data in `server/data/` — persists across restarts
- **HLS segments** are written to your system's temp folder and cleaned up automatically after 30 minutes of inactivity
- Stream copy is used for H.264 content (fast start), re-encode for other codecs
- Subtitle files (`.srt`, `.vtt`) placed in the movie folder are auto-detected