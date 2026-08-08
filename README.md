# Audixt — Audio Translator

Upload an audio file or record one with your mic, get it transcribed, review and edit the transcript, get it translated, review and edit the translation, and receive a newly synthesized spoken track in the target language — fully downloadable as MP3.

**Live app:** https://audio-translator-frontend.onrender.com

---

## Table of Contents

- [Features](#features)
- [Architecture](#architecture)
- [Tech Stack](#tech-stack)
- [How It Works (Pipeline)](#how-it-works-pipeline)
- [Project Structure](#project-structure)
- [Environment Variables](#environment-variables)
- [Running Locally](#running-locally)
- [Deployment (Render)](#deployment-render)
- [Design Decisions](#design-decisions)
- [Problems Faced & How They Were Fixed](#problems-faced--how-they-were-fixed)
- [Known Limitations / Follow-ups](#known-limitations--follow-ups)

---

## Features

- 🎙️ **Two input methods** — upload an existing audio file, or record directly from the browser mic
- 📝 **Editable transcript review** — before translation happens, the user can review and correct the transcript
- 🌐 **Editable translation review** — before speech is generated, the user can review and correct the translated text
- 🔊 **Final synthesized voice output** — downloadable MP3 in the target language
- 🌍 **40+ languages** supported for translation
- 🌓 Light/dark theme
- 📡 Real-time job status polling with a visual progress rail

---

## Architecture

```
                    ┌─────────────────────────┐
                    │   React (Vite) Frontend  │
                    │  Static Site on Render    │
                    └────────────┬─────────────┘
                                 │ HTTPS (CORS)
                                 ▼
                    ┌─────────────────────────┐
                    │   Spring Boot Backend     │
                    │  Docker Web Service        │
                    │  on Render                 │
                    └───┬──────────┬───────────┘
                        │          │
         ┌──────────────┘          └───────────────┐
         ▼                                          ▼
┌─────────────────┐                        ┌──────────────────┐
│   PostgreSQL      │                        │   AWS S3           │
│  (Render-managed)  │                        │  (audio storage)   │
└─────────────────┘                        └──────────────────┘

Pipeline services called by the backend:
  • Deepgram API        → speech-to-text
  • Python (deep-translator) → translation, shelled out via ProcessBuilder
  • Python (gTTS)        → text-to-speech, shelled out via ProcessBuilder
```

---

## Tech Stack

**Backend**
- Java 17, Spring Boot 4.1.0, Maven
- Spring Data JPA / Hibernate + PostgreSQL
- `RestTemplate` for the Deepgram API
- `ProcessBuilder` shelling out to two Python scripts for translation and TTS
- AWS SDK (S3 for file storage — Transcribe, Translate, and Polly SDKs are present in `pom.xml` but unused; see [Design Decisions](#design-decisions))

**Frontend**
- React 18 + Vite
- `MediaRecorder` API for in-browser mic recording
- `XMLHttpRequest` for upload progress tracking, `fetch` for polling
- lucide-react icons

**Python (invoked from Java)**
- `translate.py` — uses `deep-translator` (Google Translate backend)
- `tts.py` — uses `gTTS` (Google Text-to-Speech)

**Infrastructure**
- Render.com — backend (Docker Web Service), frontend (Static Site), PostgreSQL (managed, free tier)
- GitHub — source control (`chitranjan86/audio-translator`)

---

## How It Works (Pipeline)

The job moves through these statuses, with two deliberate pause points for user review:

```
PENDING            → uploading + running transcription
TRANSCRIBED        → ⏸ PAUSED — user reviews/edits transcript, then confirms
TRANSLATING        → running translation
TRANSLATED         → ⏸ PAUSED — user reviews/edits translation, then confirms
SYNTHESIZING       → generating speech
COMPLETED          → done, MP3 available for playback/download
FAILED             → any stage errored out
```

### API Endpoints

| Method | Endpoint | Purpose |
|---|---|---|
| `POST` | `/upload` | Upload/record audio, creates a job, runs transcription (Stage 1) |
| `GET` | `/jobs/{id}` | Poll job status/results |
| `GET` | `/jobs` | List all jobs |
| `POST` | `/jobs/{id}/confirm-transcript` | Submit reviewed transcript, triggers translation (Stage 2) |
| `POST` | `/jobs/{id}/confirm-translation` | Submit reviewed translation, triggers speech synthesis (Stage 3) |
| `GET` | `/jobs/{id}/audio` | Stream/download the final MP3 |
| `GET` | `/jobs/{id}/transcribe-status` | Check raw Deepgram job status (diagnostic) |

---

## Project Structure

```
audiotranslator/
├── src/main/java/com/audixt/audiotranslator/
│   ├── AudiotranslatorApplication.java
│   ├── UploadController.java        # Stage 1: upload + kick off transcription
│   ├── JobActionController.java     # Stage 2 & 3: confirm-transcript / confirm-translation
│   ├── JobController.java           # job status polling, audio streaming
│   ├── PipelineService.java         # orchestrates all 3 pipeline stages
│   ├── DeepgramService.java         # speech-to-text via Deepgram API
│   ├── TranslateService.java        # shells out to translate.py
│   ├── TtsService.java              # shells out to tts.py
│   ├── S3Service.java               # uploads original audio to S3
│   ├── AwsConfig.java
│   ├── CorsConfig.java              # allowed origins for the frontend
│   ├── Job.java                     # JPA entity
│   └── JobRepository.java
├── translate.py                     # deep-translator script
├── tts.py                           # gTTS script
├── application.properties
├── Dockerfile
├── pom.xml
└── front-end/
    ├── src/main.jsx                 # main React app
    ├── src/styles.css
    ├── index.html
    └── package.json
```

---

## Environment Variables

### Backend (set in Render → Web Service → Environment)

| Variable | Purpose |
|---|---|
| `DATABASE_URL` | JDBC connection string, format: `jdbc:postgresql://host/db` |
| `DATABASE_USERNAME` | Postgres username |
| `DATABASE_PASSWORD` | Postgres password |
| `AWS_ACCESS_KEY_ID` | AWS credentials (S3 access) |
| `AWS_SECRET_ACCESS_KEY` | AWS credentials (S3 access) |
| `DEEPGRAM_API_KEY` | Deepgram transcription API key |
| `PORT` | Auto-set by Render — app binds via `server.port=${PORT:8080}` |

### Frontend (set in Render → Static Site → Environment)

| Variable | Purpose |
|---|---|
| `VITE_API_URL` | Base URL of the deployed backend |

---

## Running Locally

**Backend**
```bash
./mvnw clean package
java -jar target/audiotranslator-0.0.1-SNAPSHOT.jar
```
Requires a local `.env`/exported environment variables matching the table above, plus Python 3 with `deep-translator` and `gTTS` installed (`pip install deep-translator gTTS`), and `translate.py`/`tts.py` present in the working directory.

**Frontend**
```bash
cd front-end
npm install
npm run dev
```
Runs on `http://localhost:5173` by default, talking to `http://localhost:8080` unless `VITE_API_URL` is overridden.

---

## Deployment (Render)

**Backend** — Docker Web Service
- Root directory: repo root (where `Dockerfile` and `pom.xml` live)
- Environment: Docker
- The `Dockerfile` is a multi-stage build: Maven+JDK builds the JAR, then a JRE-based final image installs Python 3 + pip, installs `deep-translator` and `gTTS`, symlinks `python3` → `python`, copies `translate.py` and `tts.py` in alongside the JAR

**Frontend** — Static Site
- Root directory: `front-end`
- Build command: `npm install && npm run build`
- Publish directory: `dist`

**Database** — Render-managed PostgreSQL, free tier (expires ~30 days after creation unless upgraded)

**CORS** — configured in `CorsConfig.java` via a global `WebMvcConfigurer`, explicitly allowing `http://localhost:5173` and the production frontend URL. (Per-controller `@CrossOrigin` annotations were removed to avoid this config being overridden.)

---

## Design Decisions

A few services were tried and swapped out before landing on the current stack:

- **AWS Transcribe** → dropped, not cost-effective for this use case → replaced with **Deepgram**
- **Amazon Polly** (text-to-speech) → dropped, insufficient language coverage → replaced with **gTTS** (Python)
- **AWS Translate** → not used → replaced with **deep-translator** (Python, Google Translate backend)

The AWS SDK dependencies for Transcribe, Translate, and Polly are still present in `pom.xml` but are not actually called anywhere — only **S3** (for storing original audio) is in active use from AWS.

Because gTTS and deep-translator are Python libraries with no equivalent Java library used here, the backend shells out to two small Python scripts via `ProcessBuilder`. This is why the Docker image needs a Python runtime installed alongside the JVM — a plain Java/JRE base image doesn't include one.

The pipeline was later restructured from a single automatic pass (upload → transcribe → translate → synthesize, no stops) into three separate stages with two pause points, so the user can catch and correct transcription or translation errors before they propagate into the final audio.

---

## Problems Faced & How They Were Fixed

### Git / GitHub
- **`node_modules` accidentally committed** — bloated the repo; removed with `git rm -r --cached front-end/node_modules` and added to `.gitignore`.
- **Stray output files committed** (`audio old/`, `translate-temp/`, `whisper-output/`, loose `.srt`/`.txt`/`.vtt`/`.tsv`/`.json` files) — untracked and gitignored.
- **CORS fix written locally but never pushed** — `git status` showed it as "modified, not staged," so Render kept redeploying the stale version until it was properly committed and pushed.
- **Diverged branches after adding a README directly on GitHub** — local and remote had different commits; resolved with `git pull origin main` followed by `git commit` to finish the merge, then `git push`.

### Render Deployment
- **Render auto-detected Node instead of Docker** — caused by the wrong root directory/environment; fixed by pointing the Web Service at the repo root and explicitly selecting Docker as the environment.
- **Raw Postgres connection string used instead of JDBC format** — `postgresql://user:pass@host/db` isn't valid for the JDBC driver; reformatted to `jdbc:postgresql://host/db` with username/password as separate env vars.
- **`application.properties` referenced literal values as env var names** — had `${audixt_user}` and `${the_actual_password}` instead of `${DATABASE_USERNAME}` / `${DATABASE_PASSWORD}`.
- **Missing AWS/Deepgram env vars** — caused `PlaceholderResolutionException` on startup until all secrets were added in Render's Environment tab.

### CORS
- Initial `CorsConfig.java` only allowed `http://localhost:5173`, so every request from the deployed frontend was blocked with a 403 on preflight (`No 'Access-Control-Allow-Origin' header is present`). Fixed by adding the production frontend URL to `allowedOrigins` — and by removing conflicting `@CrossOrigin` annotations on individual controllers so the global config is the single source of truth.

### Deepgram
- **`IllegalArgumentException: Illegal character(s) in message header value`** — the `DEEPGRAM_API_KEY` environment variable had stray whitespace/invisible characters from a copy-paste, which broke the HTTP `Authorization` header. Fixed by re-entering the key cleanly.

### Python / Docker
- **`Cannot run program "python": No such file or directory`** — the Docker image was JRE-only with no Python installed at all, but `TranslateService.java`/`TtsService.java` both shell out to Python scripts via `ProcessBuilder`.
- **Python scripts not present in the deployed container** — even after installing Python, `translate.py` and `tts.py` still weren't in the image, since the Dockerfile only copied the built JAR.
- **Fixed** by rewriting the Dockerfile to install `python3`/`pip3`, symlink `python3` → `python` (so the existing Java code calling `"python"` needed no changes), `pip install deep-translator gTTS`, and explicitly `COPY` both scripts into the image.

---

## Known Limitations / Follow-ups

- Free-tier Render services spin down after ~15 minutes of inactivity (30–60s cold start on the next request).
- The free Postgres instance expires ~30 days after creation unless upgraded to a paid plan.
- The Postgres password was exposed in plaintext during debugging at one point — rotated via Render's **Credential Rotation** feature; worth doing again if ever exposed similarly.
- No custom domain yet — currently on Render's free `.onrender.com` subdomains; a branded domain (e.g. `audixt.com`) would need to be purchased separately from a registrar and connected via Render's Custom Domains settings.
- AWS Transcribe, Translate, and Polly SDK dependencies remain in `pom.xml` unused — could be removed to slim down the build.
- Microphone recording requires HTTPS (or `localhost`) per browser security requirements — already satisfied in production since Render serves everything over HTTPS.

---

## Credits

Built with Spring Boot, React, Deepgram, deep-translator, and gTTS. Deployed on Render.
