package com.audixt.audiotranslator;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;

@Service
public class PipelineService {

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private S3Service s3Service;

    @Autowired
    private DeepgramService deepgramService;

    @Autowired
    private TranslateService translateService;

    @Autowired
    private TtsService ttsService;

    // STAGE 1: upload -> transcribe. Pipeline PAUSES here (status TRANSCRIBED)
    // so the user can review/edit the transcript before translation runs.
    @Async
    public void runTranscription(Long jobId, File audioFile, String sourceLanguage) {
        try {
            Job job = jobRepository.findById(jobId).orElseThrow();

            s3Service.uploadFile(audioFile);

            String transcript = deepgramService.transcribe(audioFile, sourceLanguage);
            job.setTranscript(transcript);
            job.setStatus("TRANSCRIBED");
            jobRepository.save(job);

        } catch (Exception e) {
            failJob(jobId);
            e.printStackTrace();
        }
    }

    // STAGE 2: confirmed transcript -> translation. Pipeline PAUSES here
    // (status TRANSLATED) so the user can review/edit the translation.
    @Async
    public void runTranslation(Long jobId, String confirmedTranscript, String sourceLanguage, String targetLanguage) {
        try {
            Job job = jobRepository.findById(jobId).orElseThrow();

            job.setTranscript(confirmedTranscript); // save whatever the user confirmed

            String translatedText = translateService.translate(confirmedTranscript, sourceLanguage, targetLanguage);
            job.setTranslatedText(translatedText);
            job.setStatus("TRANSLATED");
            jobRepository.save(job);

        } catch (Exception e) {
            failJob(jobId);
            e.printStackTrace();
        }
    }

    // STAGE 3: confirmed translation -> speech. Final stage.
    @Async
    public void runSynthesis(Long jobId, String confirmedTranslatedText, String targetLanguage) {
        try {
            Job job = jobRepository.findById(jobId).orElseThrow();

            job.setTranslatedText(confirmedTranslatedText); // save whatever the user confirmed

            String projectRoot = System.getProperty("user.dir");
            String outputAudioPath = projectRoot + "/uploads/output-" + jobId + "-" + java.util.UUID.randomUUID() + ".mp3";
            ttsService.synthesizeSpeech(confirmedTranslatedText, targetLanguage, outputAudioPath);

            job.setOutputAudioPath(outputAudioPath);
            job.setStatus("COMPLETED");
            jobRepository.save(job);

        } catch (Exception e) {
            failJob(jobId);
            e.printStackTrace();
        }
    }

    private void failJob(Long jobId) {
        Job job = jobRepository.findById(jobId).orElse(null);
        if (job != null) {
            job.setStatus("FAILED");
            jobRepository.save(job);
        }
    }
}