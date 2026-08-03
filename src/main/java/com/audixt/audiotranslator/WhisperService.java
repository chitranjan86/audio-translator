package com.audixt.audiotranslator;

import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class WhisperService {

    // Runs Whisper on a local audio file and returns the transcribed text.
    // languageCode should be short codes like "hi", "en", "kn"
    public String transcribe(File audioFile, String languageCode) {
        try {
            // Whisper saves its output as a .txt file in this folder
            String outputDir = System.getProperty("user.dir") + "/whisper-output";
            new File(outputDir).mkdirs();

            // Build the same command you ran manually, but from Java
            ProcessBuilder processBuilder = new ProcessBuilder(
                    "whisper",
                    audioFile.getAbsolutePath(),
                    "--model", "small",
                    "--language", languageCode,
                    "--task", "transcribe",
                    "--output_dir", outputDir,
                    "--output_format", "txt"
            );

            processBuilder.environment().put("PYTHONIOENCODING", "utf-8");
            processBuilder.redirectErrorStream(true); // merge error output with normal output
            Process process = processBuilder.start();

            // Read whisper's live output (progress bars etc.) - we don't
            // really need this text, just letting the process run to completion
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("[Whisper] " + line); // just for us to see progress in our own terminal
            }

            process.waitFor(); // wait until whisper is completely finished

            // Whisper names the output file after the input file, e.g. arvindm.txt
            String baseName = audioFile.getName().replaceFirst("[.][^.]+$", ""); // strip extension
            Path outputFile = Paths.get(outputDir, baseName + ".txt");

            // Read the transcribed text from that file
            String transcript = Files.readString(outputFile);
            return transcript.trim();

        } catch (Exception e) {
            throw new RuntimeException("Whisper transcription failed", e);
        }
    }
}