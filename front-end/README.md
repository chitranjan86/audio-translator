# Audixt Frontend

A polished React/Vite frontend for the existing Spring Boot Audixt backend.

## Backend API expected

The frontend uses the existing endpoints:

- `POST /upload`
  - multipart field `file`
  - text field `sourceLanguage`
  - text field `targetLanguage`
- `GET /jobs/{id}`
- `GET /jobs/{id}/audio`

The current `/upload` response is expected to contain a job ID like:

`Job created! ID = 1. Check status at /jobs/1`

## Run

1. Install Node.js.
2. Open this folder in VS Code.
3. Run:

```bash
npm install
npm run dev
```

The frontend normally runs at `http://localhost:5173`.

## API URL

Copy `.env.example` to `.env`:

```text
VITE_API_URL=http://localhost:8080
```

If the Spring Boot backend is elsewhere, change the value.

## Important: CORS

Because the React dev server and Spring Boot server use different ports, the backend must allow `http://localhost:5173`.

Add a CORS configuration to the Spring Boot project if you get a browser CORS error.

Example:

```java
@Configuration
public class CorsConfig {
    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**")
                        .allowedOrigins("http://localhost:5173")
                        .allowedMethods("*");
            }
        };
    }
}
```

## Language list

The UI currently uses the core language set supported by your Deepgram Nova-2 transcription configuration and commonly supported by Google translation. Your `translate.py` uses `deep-translator`/Google Translate, while `tts.py` uses gTTS, so TTS compatibility should also be tested for every language you expose.

The language dropdown is searchable and scrollable.

## Features

- Dark/light theme switch with localStorage persistence
- Drag-and-drop audio upload
- Searchable and scrollable language selector
- Source/target language swap
- Upload progress/job polling
- Original audio preview
- Translation status
- Transcript + translated text
- Translated MP3 player
- MP3 download
- Responsive mobile layout
