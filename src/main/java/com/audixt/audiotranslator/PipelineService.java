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

    // @Async makes this method run on a separate background thread -
    // whoever calls it does NOT wait for it to finish
    @Async
    public void processJob(Long jobId, File audioFile, String sourceLanguage, String targetLanguage) {
        try {
            Job job = jobRepository.findById(jobId).orElseThrow();

            String s3Key = s3Service.uploadFile(audioFile);

            String transcript = deepgramService.transcribe(audioFile, sourceLanguage);
            job.setTranscript(transcript);
            job.setStatus("TRANSCRIBED");
            jobRepository.save(job);

            String translatedText = translateService.translate(transcript, sourceLanguage, targetLanguage);
            job.setTranslatedText(translatedText);
            job.setStatus("TRANSLATED");
            jobRepository.save(job);

            String projectRoot = System.getProperty("user.dir");
            String outputAudioPath = projectRoot + "/uploads/output-" + jobId + "-" + java.util.UUID.randomUUID() + ".mp3";
            ttsService.synthesizeSpeech(translatedText, targetLanguage, outputAudioPath);

            job.setOutputAudioPath(outputAudioPath);
            job.setStatus("COMPLETED");
            jobRepository.save(job);

        } catch (Exception e) {
            // if anything fails, mark the job as FAILED instead of leaving it stuck
            Job job = jobRepository.findById(jobId).orElse(null);
            if (job != null) {
                job.setStatus("FAILED");
                jobRepository.save(job);
            }
            e.printStackTrace();
        }
    }
}