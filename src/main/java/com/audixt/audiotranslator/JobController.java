package com.audixt.audiotranslator;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.CrossOrigin;
import java.io.File;
import java.util.List;

@RestController
@CrossOrigin(origins = "http://localhost:5173")
public class JobController {

    @Autowired
    private TranscribeService transcribeService;

    @Autowired
    private JobRepository jobRepository;

    @GetMapping("/jobs/{id}/transcribe-status")
    public String checkTranscribeStatus(@PathVariable Long id) {
        Job job = jobRepository.findById(id).orElse(null);
        if (job == null) {
            return "Job not found";
        }

        var response = transcribeService.getJobStatus(job.getTranscribeJobName());
        String status = response.transcriptionJob().transcriptionJobStatusAsString();

        return "Transcription status: " + status;
    }

    // shows ALL jobs ever created
    @GetMapping("/jobs")
    public List<Job> getAllJobs() {
        return jobRepository.findAll(); // comes free from JpaRepository
    }

    // shows ONE job by its id
    @GetMapping("/jobs/{id}")
    public Job getJobById(@PathVariable Long id) {
        return jobRepository.findById(id).orElse(null);
    }

    // NEW: streams the finished TTS audio file back to the browser so the
    // frontend can play it or offer a download link, once status == DONE
    // and outputAudioPath has been set by the pipeline.
    @GetMapping("/jobs/{id}/audio")
    public ResponseEntity<FileSystemResource> getJobAudio(@PathVariable Long id) {
        Job job = jobRepository.findById(id).orElse(null);
        if (job == null || job.getOutputAudioPath() == null) {
            return ResponseEntity.notFound().build();
        }

        File audioFile = new File(job.getOutputAudioPath());
        if (!audioFile.exists()) {
            return ResponseEntity.notFound().build();
        }

        FileSystemResource resource = new FileSystemResource(audioFile);

        // gTTS/Polly output is mp3 in this pipeline; change the media type
        // here if a different format is ever produced.
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("audio/mpeg"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + audioFile.getName() + "\"")
                .body(resource);
    }
}