package com.audixt.audiotranslator;

import org.springframework.stereotype.Service;

import java.io.File;
import java.io.PrintWriter;
import java.util.UUID;

@Service
public class TtsService {

    public File synthesizeSpeech(String text, String languageCode, String outputPath) {
        try {
            String workDir = System.getProperty("user.dir") + "/tts-temp";
            new File(workDir).mkdirs();

            String textFilePath = workDir + "/" + UUID.randomUUID() + ".txt";
            try (PrintWriter writer = new PrintWriter(textFilePath, "UTF-8")) {
                writer.print(text);
            }

            ProcessBuilder processBuilder = new ProcessBuilder(
                    "python",
                    System.getProperty("user.dir") + "/tts.py",
                    textFilePath,
                    languageCode,
                    outputPath
            );
            processBuilder.environment().put("PYTHONIOENCODING", "utf-8");
            processBuilder.redirectErrorStream(true);

            Process process = processBuilder.start();

            try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("[TTS] " + line);
                }
            }

            process.waitFor();

            return new File(outputPath);

        } catch (Exception e) {
            throw new RuntimeException("Text-to-speech failed", e);
        }
    }
}