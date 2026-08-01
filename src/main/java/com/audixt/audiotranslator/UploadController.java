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

    // This tells Spring: "give me the JobRepository you built automatically,
    // I want to use it here." We don't create it ourselves - Spring hands
    // us a ready-to-use one. This is called "dependency injection."
    @Autowired
    private JobRepository jobRepository;

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

            // Create a new Job "row" and fill in its details
            Job job = new Job();
            job.setFileName(file.getOriginalFilename());
            job.setSourceLanguage(sourceLanguage);
            job.setTargetLanguage(targetLanguage);
            job.setStatus("PENDING");

            // .save() comes for free from JpaRepository - this actually
            // writes the row into the database
            Job savedJob = jobRepository.save(job);

            return "Job created! ID = " + savedJob.getId() + ", status = " + savedJob.getStatus();

        } catch (IOException e) {
            return "Failed to save file: " + e.getMessage();
        }
    }
}