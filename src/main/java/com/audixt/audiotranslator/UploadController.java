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

            // NEW: upload the same file to S3
            String s3Key = s3Service.uploadFile(destination);

            Job job = new Job();
            job.setFileName(file.getOriginalFilename());
            job.setSourceLanguage(sourceLanguage);
            job.setTargetLanguage(targetLanguage);
            job.setStatus("UPLOADED_TO_S3");

            Job savedJob = jobRepository.save(job);

            return "Job created! ID = " + savedJob.getId() + ", uploaded to S3 as: " + s3Key;

        } catch (IOException e) {
            return "Failed to save file: " + e.getMessage();
        }
    }
}