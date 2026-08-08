# Audixt — Audio Translator
### Project Architecture Notes

---

## 1. What the project does

Audixt takes an uploaded audio file, transcribes it, translates the transcript into a target language, and generates a new spoken audio track (text-to-speech) in that language. The user gets back:
- The original transcript
- The translated text
- A downloadable MP3 of the translated speech

Flow: **Upload → Transcribe (Deepgram) → Translate (Python/deep-translator) → Text-to-Speech (Python/gTTS) → Return MP3**

---

## 2. Tech Stack

### Backend
- **Framework**: Spring Boot 4.1.0 (Java 17)
- **Build tool**: Maven
- **Database**: PostgreSQL (via Spring Data JPA / Hibernate)
- **File storage**: AWS S3
- **Speech-to-text**: Deepgram API (called via `RestTemplate`)
- **Translation**: Python script (`translate.py`) using the `deep-translator` library (Google Translate backend), invoked from Java via `ProcessBuilder`
- **Text-to-speech**: Python script (`tts.py`) using the `gTTS` (Google Text-to-Speech) library, also invoked via `ProcessBuilder`
- **AWS SDKs included**: Transcribe, Translate, Polly, S3 (though Polly ended up unused — TTS is done via gTTS/Python instead)

### Frontend
- **Framework**: React (built with Vite)
- **UI**: Custom components, language picker with search, drag-and-drop file upload, live progress/status polling
- **Icons**: lucide-react
- Talks to the backend via `XMLHttpRequest` for upload (to track progress) and `fetch` for polling job status

### Infrastructure / Hosting
- **Platform**: Render.com
- **Backend**: Deployed as a Docker-based Web Service
- **Frontend**: Deployed as a Static Site (Vite build output)
- **Database**: Render-managed PostgreSQL (free tier)
- **Source control**: GitHub (`chitranjan86/audio-translator`)

---

## 3. Backend Architecture

```
Controller (upload endpoint)
      ↓
PipelineService (orchestrates the flow, runs async)
      ↓
DeepgramService.transcribe()       → calls Deepgram API over HTTP
      ↓
TranslateService.translate()       → shells out to translate.py (Python)
      ↓
TtsService.synthesizeSpeech()      → shells out to tts.py (Python)
      ↓
Job status + results stored in Postgres, polled by frontend via /jobs/{id}
```

Key backend files:
- `DeepgramService.java` — calls Deepgram's transcription API
- `TranslateService.java` — writes transcript to a temp `.txt` file, calls `translate.py` via `ProcessBuilder`, reads translated result back
- `TtsService.java` — writes translated text to a temp file, calls `tts.py` via `ProcessBuilder`, produces an MP3
- `PipelineService.java` — orchestrates the full job pipeline, runs asynchronously
- `CorsConfig.java` — configures allowed origins for cross-origin requests from the frontend
- `application.properties` — datasource, AWS, Deepgram, and multipart/timeout configuration, all pulling secrets from environment variables

### Why Python is involved
Translation and TTS aren't done in pure Java — the backend shells out to two small Python scripts (`translate.py`, `tts.py`) at the repo root, using Java's `ProcessBuilder`. This meant the Docker image had to be updated to install Python 3 and the required pip packages (`deep-translator`, `gTTS`), and to copy both `.py` scripts into the image alongside the JAR — none of which is included by a plain Java/JRE base image.

---

## 4. Frontend Architecture

- Single-page React app (Vite build)
- Reads the backend's base URL from `VITE_API_URL` (environment variable baked in at build time)
- Upload flow:
  1. User drags/selects an audio file and picks source/target languages
  2. `XMLHttpRequest` POSTs the file to `/upload`, tracking upload progress
  3. Backend returns a job ID
  4. Frontend polls `GET /jobs/{id}` every ~1.4 seconds until status is `COMPLETED` or `FAILED`
  5. On completion, displays transcript, translated text, and an embedded/downloadable MP3 player

---

## 5. Environment Variables (Backend, set in Render)

| Variable | Purpose |
|---|---|
| `DATABASE_URL` | JDBC connection string to Postgres |
| `DATABASE_USERNAME` | Postgres username |
| `DATABASE_PASSWORD` | Postgres password |
| `AWS_ACCESS_KEY_ID` | AWS credentials for S3 |
| `AWS_SECRET_ACCESS_KEY` | AWS credentials for S3 |
| `DEEPGRAM_API_KEY` | Deepgram transcription API key |

## 6. Environment Variables (Frontend, set in Render)

| Variable | Purpose |
|---|---|
| `VITE_API_URL` | Base URL of the deployed backend, used for all API calls |

---

## 7. Deployment Setup

### Backend (Render Web Service)
- Built from a **Dockerfile** at the repo root (multi-stage build)
- Stage 1: Maven + JDK 21 image builds the JAR
- Stage 2: JRE 21 image, with Python 3 + pip installed, `deep-translator` and `gTTS` pip-installed, both Python scripts copied in, then the JAR is copied and run
- Listens on the port Render assigns via `PORT` env var (mapped through `server.port=${PORT:8080}`)

### Frontend (Render Static Site)
- Build command: `npm install && npm run build`
- Publish directory: `dist`
- Root directory: `front-end` (or wherever the Vite project lives in the repo)

### Database (Render PostgreSQL)
- Free-tier instance, region: Oregon (US West)
- Free tier expires ~30 days after creation unless upgraded

---

## 8. CORS

Configured via a global `WebMvcConfigurer` bean in `CorsConfig.java`, explicitly allowing:
- `http://localhost:5173` (local dev)
- `https://audio-translator-frontend.onrender.com` (production frontend)

This was a real issue during deployment — the config initially only allowed `localhost`, causing all production requests to fail with a CORS preflight 403 until the production origin was added and actually committed/pushed (an early attempt was written locally but never committed).

---

## 9. Issues Encountered & Fixed During Deployment

1. **Render auto-detected Node instead of Docker** — fixed by ensuring the backend's root directory pointed to the repo root (where `pom.xml`/`Dockerfile` live) and manually setting the environment to Docker.
2. **`node_modules` was committed to git** — removed via `git rm -r --cached`, added to `.gitignore`.
3. **Datasource env vars misconfigured** — `spring.datasource.username`/`password` were initially set to `${audixt_user}` and `${the_actual_password}` (i.e., referencing the literal values as if they were env var *names*), instead of `${DATABASE_USERNAME}` / `${DATABASE_PASSWORD}`.
4. **Raw Postgres connection string used instead of JDBC format** — `postgresql://user:pass@host/db` doesn't work with the JDBC driver; had to be reformatted to `jdbc:postgresql://host/db` with username/password passed separately.
5. **Missing AWS/Deepgram env vars** — caused a `PlaceholderResolutionException` on startup until all required secrets were added in Render's environment settings.
6. **CORS misconfiguration** — as described above.
7. **Deepgram "Illegal character(s) in message header value"** — caused by a malformed/whitespace-contaminated `DEEPGRAM_API_KEY` value breaking the HTTP Authorization header.
8. **Python not available in the container** — `ProcessBuilder` calls to `"python"` failed because the base JRE Docker image has no Python installed at all.
9. **Python scripts not present in the deployed image** — the Dockerfile only copied the built JAR; `translate.py` and `tts.py` also needed to be explicitly `COPY`'d into the image, and Python 3 + the correct pip packages installed via `apt-get`/`pip3`.

---
# Audixt — Audio Translator
### Project Architecture Notes

---

## 1. What the project does

Audixt takes an uploaded audio file, transcribes it, translates the transcript into a target language, and generates a new spoken audio track (text-to-speech) in that language. The user gets back:
- The original transcript
- The translated text
- A downloadable MP3 of the translated speech

Flow: **Upload → Transcribe (Deepgram) → Translate (Python/deep-translator) → Text-to-Speech (Python/gTTS) → Return MP3**

---

## 2. Tech Stack

### Backend
- **Framework**: Spring Boot 4.1.0 (Java 17)
- **Build tool**: Maven
- **Database**: PostgreSQL (via Spring Data JPA / Hibernate)
- **File storage**: AWS S3
- **Speech-to-text**: Deepgram API (called via `RestTemplate`)
- **Translation**: Python script (`translate.py`) using the `deep-translator` library (Google Translate backend), invoked from Java via `ProcessBuilder`
- **Text-to-speech**: Python script (`tts.py`) using the `gTTS` (Google Text-to-Speech) library, also invoked via `ProcessBuilder`
- **AWS SDKs included**: Transcribe, Translate, Polly, S3 (though Polly ended up unused — TTS is done via gTTS/Python instead)

### Frontend
- **Framework**: React (built with Vite)
- **UI**: Custom components, language picker with search, drag-and-drop file upload, live progress/status polling
- **Icons**: lucide-react
- Talks to the backend via `XMLHttpRequest` for upload (to track progress) and `fetch` for polling job status

### Infrastructure / Hosting
- **Platform**: Render.com
- **Backend**: Deployed as a Docker-based Web Service
- **Frontend**: Deployed as a Static Site (Vite build output)
- **Database**: Render-managed PostgreSQL (free tier)
- **Source control**: GitHub (`chitranjan86/audio-translator`)

---

## 3. Backend Architecture

```
Controller (upload endpoint)
      ↓
PipelineService (orchestrates the flow, runs async)
      ↓
DeepgramService.transcribe()       → calls Deepgram API over HTTP
      ↓
TranslateService.translate()       → shells out to translate.py (Python)
      ↓
TtsService.synthesizeSpeech()      → shells out to tts.py (Python)
      ↓
Job status + results stored in Postgres, polled by frontend via /jobs/{id}
```

Key backend files:
- `DeepgramService.java` — calls Deepgram's transcription API
- `TranslateService.java` — writes transcript to a temp `.txt` file, calls `translate.py` via `ProcessBuilder`, reads translated result back
- `TtsService.java` — writes translated text to a temp file, calls `tts.py` via `ProcessBuilder`, produces an MP3
- `PipelineService.java` — orchestrates the full job pipeline, runs asynchronously
- `CorsConfig.java` — configures allowed origins for cross-origin requests from the frontend
- `application.properties` — datasource, AWS, Deepgram, and multipart/timeout configuration, all pulling secrets from environment variables

### Why Python is involved
Translation and TTS aren't done in pure Java — the backend shells out to two small Python scripts (`translate.py`, `tts.py`) at the repo root, using Java's `ProcessBuilder`. This meant the Docker image had to be updated to install Python 3 and the required pip packages (`deep-translator`, `gTTS`), and to copy both `.py` scripts into the image alongside the JAR — none of which is included by a plain Java/JRE base image.

---

## 4. Frontend Architecture

- Single-page React app (Vite build)
- Reads the backend's base URL from `VITE_API_URL` (environment variable baked in at build time)
- Upload flow:
  1. User drags/selects an audio file and picks source/target languages
  2. `XMLHttpRequest` POSTs the file to `/upload`, tracking upload progress
  3. Backend returns a job ID
  4. Frontend polls `GET /jobs/{id}` every ~1.4 seconds until status is `COMPLETED` or `FAILED`
  5. On completion, displays transcript, translated text, and an embedded/downloadable MP3 player

---

## 5. Environment Variables (Backend, set in Render)

| Variable | Purpose |
|---|---|
| `DATABASE_URL` | JDBC connection string to Postgres |
| `DATABASE_USERNAME` | Postgres username |
| `DATABASE_PASSWORD` | Postgres password |
| `AWS_ACCESS_KEY_ID` | AWS credentials for S3 |
| `AWS_SECRET_ACCESS_KEY` | AWS credentials for S3 |
| `DEEPGRAM_API_KEY` | Deepgram transcription API key |

## 6. Environment Variables (Frontend, set in Render)

| Variable | Purpose |
|---|---|
| `VITE_API_URL` | Base URL of the deployed backend, used for all API calls |

---

## 7. Deployment Setup

### Backend (Render Web Service)
- Built from a **Dockerfile** at the repo root (multi-stage build)
- Stage 1: Maven + JDK 21 image builds the JAR
- Stage 2: JRE 21 image, with Python 3 + pip installed, `deep-translator` and `gTTS` pip-installed, both Python scripts copied in, then the JAR is copied and run
- Listens on the port Render assigns via `PORT` env var (mapped through `server.port=${PORT:8080}`)

### Frontend (Render Static Site)
- Build command: `npm install && npm run build`
- Publish directory: `dist`
- Root directory: `front-end` (or wherever the Vite project lives in the repo)

### Database (Render PostgreSQL)
- Free-tier instance, region: Oregon (US West)
- Free tier expires ~30 days after creation unless upgraded

---

## 8. CORS

Configured via a global `WebMvcConfigurer` bean in `CorsConfig.java`, explicitly allowing:
- `http://localhost:5173` (local dev)
- `https://audio-translator-frontend.onrender.com` (production frontend)

This was a real issue during deployment — the config initially only allowed `localhost`, causing all production requests to fail with a CORS preflight 403 until the production origin was added and actually committed/pushed (an early attempt was written locally but never committed).

---

## 9. Design Decisions & Difficulties (Choosing the Tech Stack)

Before settling on Deepgram + Python-based translate/TTS, a few other services were tried and dropped:

- **AWS Transcribe (speech-to-text)** — considered first since the AWS SDK was already in the project (`software.amazon.awssdk:transcribe`), but it wasn't free/cheap enough for this use case, so it was dropped in favor of **Deepgram**, which was used instead for transcription.
- **Amazon Polly (text-to-speech)** — also included in the AWS SDK dependencies and tried initially, but Polly didn't support enough languages for the app's needs. Switched to **gTTS** (Google Text-to-Speech, via a Python script) instead, which covers a much wider set of languages.
- **AWS Translate** — the AWS SDK for Translate is also present in `pom.xml`, but the project ended up using a Python script with the **`deep-translator`** library (Google Translate backend) rather than AWS's own translation service.

Net effect: the AWS SDK dependencies for Transcribe, Translate, and Polly are still listed in `pom.xml`, but none of them are actually used in the final pipeline — S3 is the only AWS service actually in active use (for file storage). Deepgram, deep-translator, and gTTS replaced them.

This is also why the backend ended up needing a Python runtime shelled out from Java (see Docker/Python difficulties below) — gTTS and deep-translator are Python libraries, not Java ones, so there was no pure-JVM equivalent readily available for the languages/quality needed.

---

## 10. Issues Encountered & Fixed During Deployment

1. **Render auto-detected Node instead of Docker** — fixed by ensuring the backend's root directory pointed to the repo root (where `pom.xml`/`Dockerfile` live) and manually setting the environment to Docker.
2. **`node_modules` was committed to git** — removed via `git rm -r --cached`, added to `.gitignore`.
3. **Datasource env vars misconfigured** — `spring.datasource.username`/`password` were initially set to `${audixt_user}` and `${the_actual_password}` (i.e., referencing the literal values as if they were env var *names*), instead of `${DATABASE_USERNAME}` / `${DATABASE_PASSWORD}`.
4. **Raw Postgres connection string used instead of JDBC format** — `postgresql://user:pass@host/db` doesn't work with the JDBC driver; had to be reformatted to `jdbc:postgresql://host/db` with username/password passed separately.
5. **Missing AWS/Deepgram env vars** — caused a `PlaceholderResolutionException` on startup until all required secrets were added in Render's environment settings.
6. **CORS misconfiguration** — as described above.
7. **Deepgram "Illegal character(s) in message header value"** — caused by a malformed/whitespace-contaminated `DEEPGRAM_API_KEY` value breaking the HTTP Authorization header.
8. **Python not available in the container** — `ProcessBuilder` calls to `"python"` failed because the base JRE Docker image has no Python installed at all.
9. **Python scripts not present in the deployed image** — the Dockerfile only copied the built JAR; `translate.py` and `tts.py` also needed to be explicitly `COPY`'d into the image, and Python 3 + the correct pip packages installed via `apt-get`/`pip3`.
Here's a rundown of every difficulty you ran into, in the order they came up:


Choosing the tech stack (before deployment):

AWS Transcribe — considered for speech-to-text, but it wasn't free/cheap enough, so you switched to Deepgram.
Amazon Polly — tried for text-to-speech, but it didn't support enough languages, so you switched to gTTS (Python-based).
(Implied by the Polly/Transcribe swap) — AWS Translate also went unused; you used a Python deep-translator script instead, likely for similar reasons (cost/flexibility).
---

## 11. Outstanding / Recommended Follow-ups

- Rotate the Postgres password (it was exposed in plaintext during debugging) via Render's **Credential Rotation** feature on the database service.
- Consider adding a custom domain (requires purchasing one from a registrar; Render's `.onrender.com` subdomain is free but a branded domain like `audixt.com` is not).
- Be aware the free Postgres instance expires ~30 days after creation unless upgraded to a paid plan.
- Free-tier Render services spin down after ~15 minutes of inactivity and take 30-60 seconds to wake up on the next request.

## 12. Outstanding / Recommended Follow-ups

- Rotate the Postgres password (it was exposed in plaintext during debugging) via Render's **Credential Rotation** feature on the database service.
- Consider adding a custom domain (requires purchasing one from a registrar; Render's `.onrender.com` subdomain is free but a branded domain like `audixt.com` is not).
- Be aware the free Postgres instance expires ~30 days after creation unless upgraded to a paid plan.
- Free-tier Render services spin down after ~15 minutes of inactivity and take 30-60 seconds to wake up on the next request.
