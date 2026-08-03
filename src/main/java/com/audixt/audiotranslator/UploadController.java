package com.audixt.audiotranslator;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;

@RestController
public class UploadController {

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private S3Service s3Service;

    @Autowired
    private WhisperService whisperService;

    @Autowired
    private TranslateService translateService;

    @Autowired
    private PollyService pollyService;
    @PostMapping("/upload")
public String uploadAudio(
        @RequestParam("file") MultipartFile file,
        @RequestParam("sourceLanguage") String sourceLanguage,
        @RequestParam("targetLanguage") String targetLanguage
) {
    try {
        String projectRoot = System.getProperty("user.dir");
        File uploadDir = new File(projectRoot, "uploads");
        if (!uploadDir.exists()) {
            uploadDir.mkdirs();
        }

        File destination = new File(uploadDir, file.getOriginalFilename());
        file.transferTo(destination);

        String s3Key = s3Service.uploadFile(destination);

        String transcript = whisperService.transcribe(destination, sourceLanguage);
        String translatedText = translateService.translate(transcript, sourceLanguage, targetLanguage);

        Job job = new Job();
        job.setFileName(file.getOriginalFilename());
        job.setSourceLanguage(sourceLanguage);
        job.setTargetLanguage(targetLanguage);
        job.setStatus("TRANSLATED");
        job.setTranscript(transcript);
        job.setTranslatedText(translatedText);

        Job savedJob = jobRepository.save(job);

        // NEW: text-to-speech
        String outputAudioPath = projectRoot + "/uploads/output-" + savedJob.getId() + ".mp3";
        pollyService.synthesizeSpeech(translatedText, targetLanguage, outputAudioPath);

        savedJob.setStatus("COMPLETED");
        savedJob.setOutputAudioPath(outputAudioPath); // NEW field
        jobRepository.save(savedJob);

        return "Job created! ID = " + savedJob.getId()
                + "\nTranscript: " + transcript
                + "\nTranslated: " + translatedText
                + "\nOutput audio saved at: " + outputAudioPath;

    } catch (IOException e) {
        return "Failed: " + e.getMessage();
    }
}
    }