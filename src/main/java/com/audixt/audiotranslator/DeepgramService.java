package com.audixt.audiotranslator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.nio.file.Files;

@Service
public class DeepgramService {

    @Value("${deepgram.apiKey}")
    private String apiKey;

    // languageCode should be short codes like "hi", "en"
    public String transcribe(File audioFile, String languageCode) {
        try {
            RestTemplate restTemplate = new RestTemplate();

            String url = "https://api.deepgram.com/v1/listen?language=" + languageCode + "&model=nova-2";

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Token " + apiKey);
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);

            byte[] audioBytes = Files.readAllBytes(audioFile.toPath());
            HttpEntity<byte[]> request = new HttpEntity<>(audioBytes, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

            // Deepgram returns a big JSON response - we just need the transcript text inside it
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(response.getBody());

            String transcript = root
                    .path("results")
                    .path("channels").get(0)
                    .path("alternatives").get(0)
                    .path("transcript")
                    .asText();

            return transcript;

        } catch (Exception e) {
            throw new RuntimeException("Deepgram transcription failed", e);
        }
    }
}