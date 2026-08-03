package com.audixt.audiotranslator;

import org.springframework.stereotype.Service;

import java.io.File;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.UUID;

@Service
public class TranslateService {

    public String translate(String text, String sourceLanguage, String targetLanguage) {
        try {
            String workDir = System.getProperty("user.dir") + "/translate-temp";
            new File(workDir).mkdirs();

            String id = UUID.randomUUID().toString();
            String inputPath = workDir + "/" + id + "_in.txt";
            String outputPath = workDir + "/" + id + "_out.txt";

            // write the transcript to a temp file for the Python script to read
            try (PrintWriter writer = new PrintWriter(inputPath, "UTF-8")) {
                writer.print(text);
            }

            ProcessBuilder processBuilder = new ProcessBuilder(
                    "python",
                    System.getProperty("user.dir") + "/translate.py",
                    inputPath,
                    sourceLanguage,
                    targetLanguage,
                    outputPath
            );
            processBuilder.environment().put("PYTHONIOENCODING", "utf-8");
            processBuilder.redirectErrorStream(true);

            Process process = processBuilder.start();

            // print any output/errors from the script into our own terminal
            new BufferedReaderPrinter(process).start();

            process.waitFor();

            return Files.readString(Paths.get(outputPath)).trim();

        } catch (Exception e) {
            throw new RuntimeException("Translation failed", e);
        }
    }

    // small helper class to read and print the python script's output live
    private static class BufferedReaderPrinter extends Thread {
        private final Process process;

        BufferedReaderPrinter(Process process) {
            this.process = process;
        }

        @Override
        public void run() {
            try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("[Translate] " + line);
                }
            } catch (Exception ignored) {
            }
        }
    }
}