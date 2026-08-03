package com.audixt.audiotranslator;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;


@RestController
public class JobController {

    @Autowired
private TranscribeService transcribeService;

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
    @Autowired
    private JobRepository jobRepository;

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
}