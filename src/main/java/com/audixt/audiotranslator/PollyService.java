package com.audixt.audiotranslator;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.polly.PollyClient;
import software.amazon.awssdk.services.polly.model.OutputFormat;
import software.amazon.awssdk.services.polly.model.SynthesizeSpeechRequest;
import software.amazon.awssdk.services.polly.model.VoiceId;

import java.io.File;

@Service
public class PollyService {

    @Value("${aws.region}")
    private String region;

    private PollyClient pollyClient;

    @PostConstruct
    private void init() {
        this.pollyClient = PollyClient.builder()
                .region(Region.of(region))
                .build();
    }

    // Converts text into a spoken .mp3 file, saved locally.
    // Returns the File object pointing to that saved mp3.
    public File synthesizeSpeech(String text, String languageCode, String outputPath) {

        // Polly needs specific "Voice" names per language - a few examples:
       VoiceId voice = switch (languageCode) {
    case "hi" -> VoiceId.ADITI;  // Hindi voice
    case "en" -> VoiceId.JOANNA; // English (US) voice
    default -> VoiceId.JOANNA;   // fallback
};

        SynthesizeSpeechRequest request = SynthesizeSpeechRequest.builder()
                .text(text)
                .voiceId(voice)
                .outputFormat(OutputFormat.MP3)
                .build();

        File outputFile = new File(outputPath);

        pollyClient.synthesizeSpeech(request, ResponseTransformer.toFile(outputFile.toPath()));

        return outputFile;
    }
}