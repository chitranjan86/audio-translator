package com.audixt.audiotranslator;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
public class JobActionController {

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private PipelineService pipelineService;

    // Called when the user has reviewed/edited the transcript and hits
    // "Confirm & Translate".
    @PostMapping("/jobs/{id}/confirm-transcript")
    public Job confirmTranscript(@PathVariable Long id, @RequestBody Map<String, String> body) {
        Job job = jobRepository.findById(id).orElseThrow();

        String editedTranscript = body.get("transcript");
        if (editedTranscript == null || editedTranscript.isBlank()) {
            throw new IllegalArgumentException("transcript cannot be empty");
        }

        job.setTranscript(editedTranscript);
        job.setStatus("TRANSLATING"); // busy state while stage 2 runs
        jobRepository.save(job);

        pipelineService.runTranslation(id, editedTranscript, job.getSourceLanguage(), job.getTargetLanguage());

        return job;
    }

    // Called when the user has reviewed/edited the translation and hits
    // "Confirm & Generate Voice".
    @PostMapping("/jobs/{id}/confirm-translation")
    public Job confirmTranslation(@PathVariable Long id, @RequestBody Map<String, String> body) {
        Job job = jobRepository.findById(id).orElseThrow();

        String editedTranslation = body.get("translatedText");
        if (editedTranslation == null || editedTranslation.isBlank()) {
            throw new IllegalArgumentException("translatedText cannot be empty");
        }

        job.setTranslatedText(editedTranslation);
        job.setStatus("SYNTHESIZING"); // busy state while stage 3 runs
        jobRepository.save(job);

        pipelineService.runSynthesis(id, editedTranslation, job.getTargetLanguage());

        return job;
    }
}