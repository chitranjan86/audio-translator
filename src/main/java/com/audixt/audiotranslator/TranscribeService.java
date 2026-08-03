package com.audixt.audiotranslator;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.transcribe.TranscribeClient;
import software.amazon.awssdk.services.transcribe.model.Media;
import software.amazon.awssdk.services.transcribe.model.StartTranscriptionJobRequest;
import software.amazon.awssdk.services.transcribe.model.GetTranscriptionJobRequest;
import software.amazon.awssdk.services.transcribe.model.GetTranscriptionJobResponse;

import java.util.UUID;

@Service
public class TranscribeService {

    @Value("${aws.s3.bucketName}")
    private String bucketName;

    @Value("${aws.region}")
    private String region;

    private TranscribeClient transcribeClient;

    // Instead of TranscribeClient.create() (which guesses the region),
    // we build it AFTER Spring has injected @Value fields, explicitly
    // telling it which region to use.
    @jakarta.annotation.PostConstruct
    private void init() {
        this.transcribeClient = TranscribeClient.builder()
                .region(Region.of(region))
                .build();
    }

    public String startTranscriptionJob(String s3Key, String sourceLanguage) {
        String jobName = "transcribe-" + UUID.randomUUID();
        String mediaUri = "s3://" + bucketName + "/" + s3Key;
        String languageCode = sourceLanguage.equals("hi") ? "hi-IN" : "en-US";

        StartTranscriptionJobRequest request = StartTranscriptionJobRequest.builder()
                .transcriptionJobName(jobName)
                .languageCode(languageCode)
                .media(Media.builder().mediaFileUri(mediaUri).build())
                .outputBucketName(bucketName)
                .build();

        transcribeClient.startTranscriptionJob(request);

        return jobName;
    }

    public GetTranscriptionJobResponse getJobStatus(String jobName) {
        GetTranscriptionJobRequest request = GetTranscriptionJobRequest.builder()
                .transcriptionJobName(jobName)
                .build();

        return transcribeClient.getTranscriptionJob(request);
    }
}