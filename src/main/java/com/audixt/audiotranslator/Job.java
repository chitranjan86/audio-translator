package com.audixt.audiotranslator;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Job {
    @jakarta.persistence.Lob
private String transcript;
public String getTranscript() {
    return transcript;
}

public void setTranscript(String transcript) {
    this.transcript = transcript;
}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String transcribeJobName;
    private String fileName;
    private String sourceLanguage;
    private String targetLanguage;
    private String status;
    public String getTranscribeJobName() {
    return transcribeJobName;
}

public void setTranscribeJobName(String transcribeJobName) {
    this.transcribeJobName = transcribeJobName;
}
    // Empty constructor - Spring/the database needs this to exist,
    // even though we don't call it directly ourselves
    public Job() {
    }

    // Getters and setters - these let other code read/change these
    // private fields safely (instead of accessing them directly)

    public Long getId() {
        return id;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getSourceLanguage() {
        return sourceLanguage;
    }

    public void setSourceLanguage(String sourceLanguage) {
        this.sourceLanguage = sourceLanguage;
    }

    public String getTargetLanguage() {
        return targetLanguage;
    }

    public void setTargetLanguage(String targetLanguage) {
        this.targetLanguage = targetLanguage;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
    @jakarta.persistence.Lob
private String translatedText;

public String getTranslatedText() {
    return translatedText;
}
private String outputAudioPath;

public String getOutputAudioPath() {
    return outputAudioPath;
}

public void setOutputAudioPath(String outputAudioPath) {
    this.outputAudioPath = outputAudioPath;
}

public void setTranslatedText(String translatedText) {
    this.translatedText = translatedText;
}
}