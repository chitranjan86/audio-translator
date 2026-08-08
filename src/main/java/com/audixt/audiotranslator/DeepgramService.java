package com.audixt.audiotranslator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.File;

@Service
public class DeepgramService {

    @Value("${deepgram.apiKey}")
    private String apiKey;

    // languageCode should be short codes like "hi", "en"
    public String transcribe(File audioFile, String languageCode) {
        try {
            // long connect/read timeouts (20 min) so a big file being uploaded
            // to Deepgram, or a long transcription job, doesn't get killed early
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(60_000);
            factory.setReadTimeout(20 * 60_000);
            RestTemplate restTemplate = new RestTemplate(factory);

            String url = "https://api.deepgram.com/v1/listen?language=" + languageCode + "&model=nova-3";

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Token " + apiKey);
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentLength(audioFile.length());

            // stream from disk instead of Files.readAllBytes() - avoids holding
            // a ~1hr audio file's entire byte array in memory at once
            FileSystemResource resource = new FileSystemResource(audioFile);
            HttpEntity<FileSystemResource> request = new HttpEntity<>(resource, headers);

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