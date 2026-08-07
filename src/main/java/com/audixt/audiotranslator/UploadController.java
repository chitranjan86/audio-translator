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
    private PipelineService pipelineService;

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

            Job job = new Job();
            job.setFileName(file.getOriginalFilename());
            job.setSourceLanguage(sourceLanguage);
            job.setTargetLanguage(targetLanguage);
            job.setStatus("PENDING");

            Job savedJob = jobRepository.save(job);

            // kick off the heavy work in the background - this call
            // returns IMMEDIATELY, doesn't wait for processing to finish
            pipelineService.processJob(savedJob.getId(), destination, sourceLanguage, targetLanguage);

            return "Job created! ID = " + savedJob.getId() + ". Check status at /jobs/" + savedJob.getId();

        } catch (IOException e) {
            return "Failed: " + e.getMessage();
        }
    }
}