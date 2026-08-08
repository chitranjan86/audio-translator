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

    // Accepts either a picked file OR a recorded mic clip - both arrive here
    // as a normal multipart file (the recorder just gives it a generated
    // filename like "recording.webm").
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

            String safeName = (file.getOriginalFilename() == null || file.getOriginalFilename().isBlank())
                    ? "recording-" + System.currentTimeMillis() + ".webm"
                    : file.getOriginalFilename();

            File destination = new File(uploadDir, safeName);
            file.transferTo(destination);

            Job job = new Job();
            job.setFileName(safeName);
            job.setSourceLanguage(sourceLanguage);
            job.setTargetLanguage(targetLanguage);
            job.setStatus("PENDING");

            Job savedJob = jobRepository.save(job);

            // Stage 1 only - transcribe, then STOP and wait for user review.
            pipelineService.runTranscription(savedJob.getId(), destination, sourceLanguage);

            return "Job created! ID = " + savedJob.getId() + ". Check status at /jobs/" + savedJob.getId();

        } catch (IOException e) {
            return "Failed: " + e.getMessage();
        }
    }
}