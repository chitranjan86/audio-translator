import React, { useEffect, useMemo, useRef, useState } from "react";
import { createRoot } from "react-dom/client";
import {
  ArrowDownToLine,
  ArrowLeftRight,
  Check,
  ChevronDown,
  Clock,
  Copy,
  FileAudio,
  Languages,
  LoaderCircle,
  Mic,
  Moon,
  Pencil,
  Play,
  Search,
  Square,
  Sun,
  UploadCloud,
  X,
  RotateCcw,
  Sparkles,
  Volume2
} from "lucide-react";
import "./styles.css";

const API_URL = import.meta.env.VITE_API_URL || "http://localhost:8080";

const LANGUAGES = [
  ["bg", "Bulgarian", "🇧🇬"],
  ["bn", "Bengali", "🇧🇩"],
  ["ca", "Catalan", "🏴"],
  ["zh", "Chinese (Simplified)", "🇨🇳"],
  ["zh-TW", "Chinese (Traditional)", "🇹🇼"],
  ["cs", "Czech", "🇨🇿"],
  ["da", "Danish", "🇩🇰"],
  ["nl", "Dutch", "🇳🇱"],
  ["en", "English", "🇺🇸"],
  ["et", "Estonian", "🇪🇪"],
  ["fi", "Finnish", "🇫🇮"],
  ["fr", "French", "🇫🇷"],
  ["de", "German", "🇩🇪"],
  ["el", "Greek", "🇬🇷"],
  ["gu", "Gujarati", "🇮🇳"],
  ["hi", "Hindi", "🇮🇳"],
  ["hu", "Hungarian", "🇭🇺"],
  ["id", "Indonesian", "🇮🇩"],
  ["it", "Italian", "🇮🇹"],
  ["ja", "Japanese", "🇯🇵"],
  ["kn", "Kannada", "🇮🇳"],
  ["ko", "Korean", "🇰🇷"],
  ["lv", "Latvian", "🇱🇻"],
  ["lt", "Lithuanian", "🇱🇹"],
  ["ml", "Malayalam", "🇮🇳"],
  ["ms", "Malay", "🇲🇾"],
  ["mr", "Marathi", "🇮🇳"],
  ["ne", "Nepali", "🇳🇵"],
  ["no", "Norwegian", "🇳🇴"],
  ["pa", "Punjabi", "🇮🇳"],
  ["pl", "Polish", "🇵🇱"],
  ["pt", "Portuguese", "🇵🇹"],
  ["ro", "Romanian", "🇷🇴"],
  ["ru", "Russian", "🇷🇺"],
  ["sk", "Slovak", "🇸🇰"],
  ["es", "Spanish", "🇪🇸"],
  ["sv", "Swedish", "🇸🇪"],
  ["ta", "Tamil", "🇮🇳"],
  ["te", "Telugu", "🇮🇳"],
  ["th", "Thai", "🇹🇭"],
  ["tr", "Turkish", "🇹🇷"],
  ["uk", "Ukrainian", "🇺🇦"],
  ["ur", "Urdu", "🇵🇰"],
  ["vi", "Vietnamese", "🇻🇳"]
].map(([code, name, flag]) => ({ code, name, flag }));

const statusLabels = {
  PENDING: "Uploading & transcribing",
  TRANSCRIBED: "Awaiting transcript review",
  TRANSLATING: "Translating",
  TRANSLATED: "Awaiting translation review",
  SYNTHESIZING: "Generating voice",
  COMPLETED: "Ready",
  FAILED: "Failed"
};

// how far along the rail each status fills, purely for the progress bar
const statusProgress = {
  PENDING: 12,
  TRANSCRIBED: 30,
  TRANSLATING: 50,
  TRANSLATED: 68,
  SYNTHESIZING: 88,
  COMPLETED: 100,
  FAILED: 100
};

function App() {
  const [theme, setTheme] = useState(() => localStorage.getItem("audixt-theme") || "dark");
  const [source, setSource] = useState("hi");
  const [target, setTarget] = useState("en");

  // "upload" or "record" - null until the user picks one
  const [mode, setMode] = useState(null);

  const [file, setFile] = useState(null);
  const [job, setJob] = useState(null);
  const [jobId, setJobId] = useState(null);
  const [uploading, setUploading] = useState(false);
  const [uploadProgress, setUploadProgress] = useState(0);
  const [error, setError] = useState("");
  const [dragging, setDragging] = useState(false);
  const [copied, setCopied] = useState("");
  const inputRef = useRef(null);

  // --- mic recording state ---
  const [isRecording, setIsRecording] = useState(false);
  const [recordSeconds, setRecordSeconds] = useState(0);
  const mediaRecorderRef = useRef(null);
  const recordedChunksRef = useRef([]);
  const recordTimerRef = useRef(null);
  const streamRef = useRef(null);

  // --- review/edit state for the two confirmation stages ---
  const [transcriptDraft, setTranscriptDraft] = useState("");
  const [translationDraft, setTranslationDraft] = useState("");
  const [confirming, setConfirming] = useState(false);

  const copyText = async (text, key) => {
    if (!text) return;
    try {
      await navigator.clipboard.writeText(text);
      setCopied(key);
      setTimeout(() => setCopied(""), 1500);
    } catch {
      setError("Couldn't copy to clipboard.");
    }
  };

  useEffect(() => {
    document.documentElement.dataset.theme = theme;
    localStorage.setItem("audixt-theme", theme);
  }, [theme]);

  // keep the editable drafts in sync whenever a fresh transcript/translation
  // comes back from the backend (but don't stomp on edits the user is
  // actively making mid-review - only sync when the value actually changes)
  useEffect(() => {
    if (job?.status === "TRANSCRIBED") {
      setTranscriptDraft(job.transcript || "");
    }
  }, [job?.status, job?.transcript]);

  useEffect(() => {
    if (job?.status === "TRANSLATED") {
      setTranslationDraft(job.translatedText || "");
    }
  }, [job?.status, job?.translatedText]);

  useEffect(() => {
    if (!jobId) return;

    let timer;
    const poll = async () => {
      try {
        const response = await fetch(`${API_URL}/jobs/${jobId}`);
        if (!response.ok) throw new Error("Could not read job status.");
        const data = await response.json();
        setJob(data);

        // keep polling through every stage, including the two paused/review
        // states - a status only stops advancing on its own once it hits
        // TRANSCRIBED or TRANSLATED, which is exactly when we want the
        // poll to keep confirming nothing changed underneath the user
        if (!["COMPLETED", "FAILED"].includes(data.status)) {
          timer = setTimeout(poll, 1400);
        }
      } catch (e) {
        setError(e.message);
      }
    };

    poll();
    return () => clearTimeout(timer);
  }, [jobId]);

  const progress = useMemo(() => {
    if (!job) return 0;
    return statusProgress[job.status] || 0;
  }, [job]);

  const chooseFile = (selected) => {
    if (!selected) return;
    if (!selected.type.startsWith("audio/") && !/\.(mp3|wav|m4a|aac|ogg|flac|webm)$/i.test(selected.name)) {
      setError("Please choose an audio file such as MP3, WAV, M4A, AAC, OGG, FLAC or WEBM.");
      return;
    }
    setError("");
    setFile(selected);
    setJob(null);
    setJobId(null);
  };

  const onDrop = (e) => {
    e.preventDefault();
    setDragging(false);
    chooseFile(e.dataTransfer.files?.[0]);
  };

  const swapLanguages = () => {
    setSource(target);
    setTarget(source);
  };

  // ---------- mic recording ----------

  const startRecording = async () => {
    setError("");
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      streamRef.current = stream;

      const recorder = new MediaRecorder(stream);
      mediaRecorderRef.current = recorder;
      recordedChunksRef.current = [];

      recorder.ondataavailable = (e) => {
        if (e.data.size > 0) recordedChunksRef.current.push(e.data);
      };

      recorder.onstop = () => {
        const blob = new Blob(recordedChunksRef.current, { type: "audio/webm" });
        const recordedFile = new File([blob], `recording-${Date.now()}.webm`, { type: "audio/webm" });
        setFile(recordedFile);
        setJob(null);
        setJobId(null);
        streamRef.current?.getTracks().forEach((t) => t.stop());
      };

      recorder.start();
      setIsRecording(true);
      setRecordSeconds(0);
      recordTimerRef.current = setInterval(() => setRecordSeconds((s) => s + 1), 1000);
    } catch (e) {
      setError("Couldn't access the microphone. Check your browser's permission settings.");
    }
  };

  const stopRecording = () => {
    mediaRecorderRef.current?.stop();
    setIsRecording(false);
    clearInterval(recordTimerRef.current);
  };

  const formatSeconds = (s) => {
    const m = Math.floor(s / 60).toString().padStart(2, "0");
    const r = (s % 60).toString().padStart(2, "0");
    return `${m}:${r}`;
  };

  // ---------- stage 1: upload/record -> kicks off transcription ----------

  const translate = async () => {
    if (!file) {
      setError("Choose an audio file or record one first.");
      return;
    }
    if (source === target) {
      setError("Choose two different languages.");
      return;
    }

    setError("");
    setUploading(true);
    setUploadProgress(0);
    setJob(null);
    setJobId(null);

    try {
      const form = new FormData();
      form.append("file", file);
      form.append("sourceLanguage", source);
      form.append("targetLanguage", target);

      const text = await new Promise((resolve, reject) => {
        const xhr = new XMLHttpRequest();
        xhr.open("POST", `${API_URL}/upload`);

        xhr.upload.onprogress = (e) => {
          if (e.lengthComputable) {
            setUploadProgress(Math.round((e.loaded / e.total) * 100));
          }
        };

        xhr.onload = () => {
          if (xhr.status >= 200 && xhr.status < 300) {
            resolve(xhr.responseText);
          } else {
            reject(new Error(xhr.responseText || `Upload failed (${xhr.status}).`));
          }
        };

        xhr.onerror = () => reject(new Error("Network error during upload. Is the backend reachable?"));

        xhr.send(form);
      });

      const match = text.match(/ID\s*=\s*(\d+)/i);

      if (!match) {
        throw new Error(
          "Backend created the job, but the job ID could not be detected. Response: " + text
        );
      }

      setJobId(match[1]);
    } catch (e) {
      setError(e.message || "Something went wrong.");
    } finally {
      setUploading(false);
      setUploadProgress(0);
    }
  };

  // ---------- stage 2: confirm transcript -> kicks off translation ----------

  const confirmTranscript = async () => {
    if (!transcriptDraft.trim()) {
      setError("Transcript can't be empty.");
      return;
    }
    setError("");
    setConfirming(true);
    try {
      const response = await fetch(`${API_URL}/jobs/${jobId}/confirm-transcript`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ transcript: transcriptDraft })
      });
      if (!response.ok) throw new Error("Could not confirm transcript.");
      const data = await response.json();
      setJob(data); // status becomes TRANSLATING, polling picks up from here
    } catch (e) {
      setError(e.message || "Something went wrong confirming the transcript.");
    } finally {
      setConfirming(false);
    }
  };

  // ---------- stage 3: confirm translation -> kicks off voice synthesis ----------

  const confirmTranslation = async () => {
    if (!translationDraft.trim()) {
      setError("Translated text can't be empty.");
      return;
    }
    setError("");
    setConfirming(true);
    try {
      const response = await fetch(`${API_URL}/jobs/${jobId}/confirm-translation`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ translatedText: translationDraft })
      });
      if (!response.ok) throw new Error("Could not confirm translation.");
      const data = await response.json();
      setJob(data); // status becomes SYNTHESIZING, polling picks up from here
    } catch (e) {
      setError(e.message || "Something went wrong confirming the translation.");
    } finally {
      setConfirming(false);
    }
  };

  const reset = () => {
    setFile(null);
    setJob(null);
    setJobId(null);
    setError("");
    setMode(null);
    setTranscriptDraft("");
    setTranslationDraft("");
    if (inputRef.current) inputRef.current.value = "";
  };

  const sourceLanguage = LANGUAGES.find((l) => l.code === source);
  const targetLanguage = LANGUAGES.find((l) => l.code === target);
  const audioUrl = job?.status === "COMPLETED" && jobId ? `${API_URL}/jobs/${jobId}/audio` : null;

  // is the job in one of the two "paused, waiting on the user" states?
  const awaitingTranscriptReview = job?.status === "TRANSCRIBED";
  const awaitingTranslationReview = job?.status === "TRANSLATED";
  const isBusy =
    uploading ||
    (job && ["PENDING", "TRANSLATING", "SYNTHESIZING"].includes(job.status));

  return (
    <div className="app-shell">
      <div className="ambient ambient-one" />
      <div className="ambient ambient-two" />

      <header className="topbar">
        <div className="brand">
          <div className="brand-mark"><Volume2 size={19} /></div>
          <span>Audixt</span>
        </div>

        <div className="top-actions">
          <div className="status-pill"><span className="live-dot" /> Spring Boot API</div>
          <button
            className="theme-btn"
            onClick={() => setTheme(theme === "dark" ? "light" : "dark")}
            aria-label="Toggle theme"
            title="Toggle theme"
          >
            {theme === "dark" ? <Sun size={18} /> : <Moon size={18} />}
          </button>
        </div>
      </header>

      <main className="main">
        <section className="hero">
          <div className="eyebrow"><Sparkles size={15} /> Transcribe · Review · Translate · Review · Resynthesize</div>
          <h1>Audio in one language.<br /><span>Voice out in another.</span></h1>
          <p>Upload a recording or record one with your mic, review and fix the transcript, review and fix the translation, then generate the final spoken track.</p>
        </section>

        <section className="translator-card">

          {/* ---------- Mode picker: only shown before a file/recording exists ---------- */}
          {!file && (
            <div className="mode-tabs">
              <button
                className={`mode-tab ${mode === "upload" ? "active" : ""}`}
                onClick={() => setMode("upload")}
              >
                <UploadCloud size={17} /> Upload file
              </button>
              <button
                className={`mode-tab ${mode === "record" ? "active" : ""}`}
                onClick={() => setMode("record")}
              >
                <Mic size={17} /> Record with mic
              </button>
            </div>
          )}

          {/* ---------- Upload mode ---------- */}
          {!file && mode === "upload" && (
            <div
              className={`dropzone ${dragging ? "dragging" : ""}`}
              onDragOver={(e) => { e.preventDefault(); setDragging(true); }}
              onDragLeave={() => setDragging(false)}
              onDrop={onDrop}
              onClick={() => inputRef.current?.click()}
            >
              <input
                ref={inputRef}
                type="file"
                accept="audio/*,.m4a,.mp3,.wav,.aac,.ogg,.flac,.webm"
                hidden
                onChange={(e) => chooseFile(e.target.files?.[0])}
              />
              <div className="upload-icon"><UploadCloud size={28} /></div>
              <strong>Drop your audio here</strong>
              <span>or click to browse · MP3, WAV, M4A, AAC and more</span>
            </div>
          )}

          {!file && mode === "upload" && (
            <div className="size-hint">
              <Clock size={13} /> Long recordings are fine — files up to ~1GB (roughly an hour of audio) are supported.
            </div>
          )}

          {/* ---------- Record mode ---------- */}
          {!file && mode === "record" && (
            <div className="record-panel">
              <div className={`record-dot-wrap ${isRecording ? "live" : ""}`}>
                <Mic size={30} />
              </div>
              <strong>{isRecording ? "Recording…" : "Ready to record"}</strong>
              <span className="record-timer">{formatSeconds(recordSeconds)}</span>
              {!isRecording ? (
                <button className="translate-btn" onClick={startRecording}>
                  <Mic size={18} /> Start recording
                </button>
              ) : (
                <button className="translate-btn danger-btn" onClick={stopRecording}>
                  <Square size={18} /> Stop recording
                </button>
              )}
            </div>
          )}

          {/* ---------- Selected file/recording preview (shared by both modes) ---------- */}
          {file && (
            <div className="file-row">
              <div className="file-info">
                <div className="mini-file"><FileAudio size={17} /></div>
                <div>
                  <b>{file.name}</b>
                  <small>{(file.size / 1024 / 1024).toFixed(2)} MB</small>
                </div>
              </div>
              <button className="icon-btn" onClick={reset} title="Remove / start over">
                <X size={17} />
              </button>
            </div>
          )}

          {file && (
            <div className="input-player">
              <audio controls src={URL.createObjectURL(file)} />
            </div>
          )}

          <div className="language-grid">
            <LanguagePicker label="FROM" value={source} onChange={setSource} />
            <button className="swap-btn" onClick={swapLanguages} title="Swap languages">
              <ArrowLeftRight size={19} />
            </button>
            <LanguagePicker label="TO" value={target} onChange={setTarget} />
          </div>

          {uploading && (
            <div className="progress-panel">
              <div className="progress-head">
                <div>
                  <small>UPLOADING FILE</small>
                  <strong>{uploadProgress < 100 ? "Sending to server" : "Finishing up"}</strong>
                </div>
                <span>{uploadProgress}%</span>
              </div>
              <div className="progress-track"><div className="progress-fill" style={{ width: `${uploadProgress}%` }} /></div>
            </div>
          )}

          {error && <div className="error-box"><X size={16} /> {error}</div>}

          {job && (
            <div className="signal-rail">
              <div className="signal-rail-head">
                <div>
                  <small>SIGNAL CHAIN</small>
                  <strong>{statusLabels[job.status] || job.status}</strong>
                </div>
                <span className="rail-status" style={{ color: job.status === "FAILED" ? "var(--danger)" : "var(--out)" }}>
                  {job.status === "FAILED" ? "Failed" : `${progress}%`}
                </span>
              </div>

              <div className="rail-track">
                <div
                  className="rail-fill"
                  style={{
                    width: `${progress}%`,
                    background: job.status === "FAILED" ? "var(--danger)" : "var(--out)"
                  }}
                />
              </div>

              <div className="rail-nodes">
                {[
                  { label: "Upload", threshold: 12 },
                  { label: "Transcribe", threshold: 30 },
                  { label: "Translate", threshold: 68 },
                  { label: "Voice", threshold: 100 }
                ].map((stage, i, arr) => {
                  const isVoiceStage = i === arr.length - 1;
                  const done = isVoiceStage
                    ? progress >= 100 && job.status === "COMPLETED"
                    : progress >= stage.threshold;
                  const prevDone = i === 0 ? true : progress >= arr[i - 1].threshold;
                  const isActive = !done && prevDone && job.status !== "FAILED";
                  const isFailed = job.status === "FAILED" && !done && prevDone;

                  return (
                    <div key={stage.label} className={`rail-node ${done ? "lit" : ""} ${isActive ? "active" : ""} ${isFailed ? "failed" : ""}`}>
                      <span className="rail-dot" />
                      <span className="rail-label">{stage.label}</span>
                    </div>
                  );
                })}
              </div>
            </div>
          )}

          {/* ---------- Stage 2 review: editable transcript ---------- */}
          {awaitingTranscriptReview && (
            <div className="review-panel">
              <div className="review-head">
                <Pencil size={15} />
                <span>Review the transcript — edit anything before it gets translated</span>
              </div>
              <textarea
                className="review-textarea"
                value={transcriptDraft}
                onChange={(e) => setTranscriptDraft(e.target.value)}
                rows={6}
              />
              <button className="translate-btn" disabled={confirming} onClick={confirmTranscript}>
                {confirming ? <><LoaderCircle className="spin" size={20} /> Confirming...</> : <><Check size={20} /> Confirm & translate</>}
              </button>
            </div>
          )}

          {/* ---------- Stage 3 review: editable translation ---------- */}
          {awaitingTranslationReview && (
            <div className="review-panel">
              <div className="review-head">
                <Pencil size={15} />
                <span>Review the translation — edit anything before the voice is generated</span>
              </div>
              <textarea
                className="review-textarea"
                value={translationDraft}
                onChange={(e) => setTranslationDraft(e.target.value)}
                rows={6}
              />
              <button className="translate-btn" disabled={confirming} onClick={confirmTranslation}>
                {confirming ? <><LoaderCircle className="spin" size={20} /> Confirming...</> : <><Check size={20} /> Confirm & generate voice</>}
              </button>
            </div>
          )}

          {/* ---------- Initial "kick off transcription" button ---------- */}
          {!job && (
            <button className="translate-btn" disabled={!file || isBusy} onClick={translate}>
              {isBusy ? (
                <><LoaderCircle className="spin" size={20} /> Processing...</>
              ) : (
                <><Languages size={20} /> Start transcription</>
              )}
            </button>
          )}
        </section>

        {job?.status === "COMPLETED" && audioUrl && (
          <section className="results">
            <div className="result-heading">
              <div>
                <div className="eyebrow"><Check size={15} /> Translation complete</div>
                <h2>Your translated audio is ready</h2>
              </div>
              <button className="secondary-btn" onClick={reset}><RotateCcw size={16} /> New translation</button>
            </div>

            <div className="result-grid">
              <div className="result-card">
                <div className="result-label-row">
                  <div className="result-label">FINAL TRANSCRIPT</div>
                  <button className="copy-btn" onClick={() => copyText(job.transcript, "transcript")} title="Copy transcript" disabled={!job.transcript}>
                    {copied === "transcript" ? <Check size={14} /> : <Copy size={14} />}
                  </button>
                </div>
                <p>{job.transcript || "Transcript not returned by the backend."}</p>
                <div className="result-language">{sourceLanguage?.flag} {sourceLanguage?.name}</div>
              </div>

              <div className="result-card accent-card">
                <div className="result-label-row">
                  <div className="result-label">FINAL TRANSLATED TEXT</div>
                  <button className="copy-btn" onClick={() => copyText(job.translatedText, "translated")} title="Copy translation" disabled={!job.translatedText}>
                    {copied === "translated" ? <Check size={14} /> : <Copy size={14} />}
                  </button>
                </div>
                <p>{job.translatedText || "Translated text not returned by the backend."}</p>
                <div className="result-language">{targetLanguage?.flag} {targetLanguage?.name}</div>
              </div>
            </div>

            <div className="audio-result">
              <div className="audio-title">
                <div className="audio-badge"><Play size={18} fill="currentColor" /></div>
                <div>
                  <b>Translated voice</b>
                  <span>{targetLanguage?.name} · MP3</span>
                </div>
              </div>
              <audio controls src={audioUrl} />
              <a className="download-btn" href={audioUrl} download={`audixt-${jobId}.mp3`} target="_blank" rel="noopener noreferrer">
                <ArrowDownToLine size={18} /> Download MP3
              </a>
            </div>
          </section>
        )}

        <footer>
          <span>© 2026 Audixt</span>
          <span>Spring Boot · Deepgram · Google Translate · gTTS</span>
        </footer>
      </main>
    </div>
  );
}

function LanguagePicker({ label, value, onChange }) {
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState("");
  const ref = useRef(null);

  useEffect(() => {
    const close = (e) => {
      if (!ref.current?.contains(e.target)) setOpen(false);
    };
    document.addEventListener("mousedown", close);
    return () => document.removeEventListener("mousedown", close);
  }, []);

  const selected = LANGUAGES.find((l) => l.code === value);
  const filtered = LANGUAGES.filter((l) =>
    `${l.name} ${l.code}`.toLowerCase().includes(query.toLowerCase())
  );

  return (
    <div className="language-wrap" ref={ref}>
      <label>{label}</label>
      <button className={`language-button ${open ? "open" : ""}`} onClick={() => setOpen(!open)}>
        <span className="language-selected">
          <span className="flag">{selected?.flag}</span>
          <span>{selected?.name}</span>
          <span className="code-badge">{selected?.code}</span>
        </span>
        <ChevronDown size={18} />
      </button>

      {open && (
        <div className="language-menu">
          <div className="search-box">
            <Search size={16} />
            <input
              autoFocus
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Search language..."
              onClick={(e) => e.stopPropagation()}
            />
          </div>
          <div className="language-list">
            {filtered.map((language) => (
              <button
                key={language.code}
                className={language.code === value ? "selected" : ""}
                onClick={() => {
                  onChange(language.code);
                  setOpen(false);
                  setQuery("");
                }}
              >
                <span className="flag">{language.flag}</span>
                <span>{language.name}</span>
                <span className="code-badge">{language.code}</span>
                {language.code === value && <Check size={16} />}
              </button>
            ))}
            {!filtered.length && <div className="no-results">No language found</div>}
          </div>
        </div>
      )}
    </div>
  );
}

createRoot(document.getElementById("root")).render(<App />);