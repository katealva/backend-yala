package com.yala.service;

import com.yala.dto.live.ResponseTranscriptDTO;
import com.yala.exceptions.ResourceNotFoundException;
import com.yala.exceptions.UnauthorizedException;
import com.yala.model.LiveStream;
import com.yala.repository.LiveStreamRepository;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.multipart.MultipartFile;

/**
 * Transcribes short audio chunks of the live's mic using OpenAI's speech-to-text
 * ({@code gpt-4o-mini-transcribe} by default). The client streams overlapping ~3s chunks and
 * stitches the returned text into a rolling buffer to detect the trigger phrase (ADR-002,
 * Fase 3 STT — replaces the Chrome-only Web Speech API with a cross-browser path). Degrades to
 * empty text when no API key is configured. Reuses the same OpenAI config as the other services.
 */
@Slf4j
@Service
public class LiveTranscriptionService {

    private final LiveStreamRepository liveStreamRepository;
    private final RestClient restClient;

    private final String apiKey;
    private final String baseUrl;
    private final String model;

    public LiveTranscriptionService(
            LiveStreamRepository liveStreamRepository,
            RestClient.Builder restClientBuilder,
            @Value("${openai.api-key:}") String apiKey,
            @Value("${openai.base-url:https://api.openai.com}") String baseUrl,
            @Value("${openai.transcribe-model:gpt-4o-mini-transcribe}") String model) {
        this.liveStreamRepository = liveStreamRepository;
        this.restClient = restClientBuilder.build();
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Transactional(readOnly = true)
    public ResponseTranscriptDTO transcribe(Long streamId, String sellerEmail, MultipartFile audio) {
        LiveStream stream = liveStreamRepository.findById(streamId)
                .orElseThrow(() -> new ResourceNotFoundException("Live stream not found with id: " + streamId));
        if (!stream.getSeller().getEmail().equals(sellerEmail)) {
            throw new UnauthorizedException("Only the host can transcribe this live's audio");
        }
        if (audio == null || audio.isEmpty() || !isConfigured()) {
            return new ResponseTranscriptDTO("");
        }
        try {
            byte[] audioBytes = audio.getBytes();
            String filename = audio.getOriginalFilename() != null && !audio.getOriginalFilename().isBlank()
                    ? audio.getOriginalFilename() : "chunk.wav";
            // Build the multipart body by hand as a byte[]: the JDK HttpClient transport can drop a
            // MultiValueMap converter body (so language/prompt wouldn't reach OpenAI), but publishes a
            // byte[] reliably — guaranteeing es + prompt + temperature are applied (reduces the wrong-
            // language hallucinations).
            String boundary = "YalaBoundary" + Long.toHexString(System.nanoTime());
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            writeField(out, boundary, "model", model);
            writeField(out, boundary, "language", "es");
            writeField(out, boundary, "response_format", "text");
            writeField(out, boundary, "temperature", "0");
            writeField(out, boundary, "prompt", TRANSCRIBE_PROMPT);
            out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n")
                    .getBytes(StandardCharsets.UTF_8));
            out.write("Content-Type: audio/wav\r\n\r\n".getBytes(StandardCharsets.UTF_8));
            out.write(audioBytes);
            out.write("\r\n".getBytes(StandardCharsets.UTF_8));
            out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

            String text = restClient.post()
                    .uri(baseUrl + "/v1/audio/transcriptions")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .body(out.toByteArray())
                    .retrieve()
                    .body(String.class);

            return new ResponseTranscriptDTO(text != null ? text.trim() : "");
        } catch (RestClientResponseException e) {
            log.warn("Live transcription HTTP {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            return new ResponseTranscriptDTO("");
        } catch (Exception e) {
            log.warn("Live transcription failed: {}", e.getMessage());
            return new ResponseTranscriptDTO("");
        }
    }

    private static final String TRANSCRIBE_PROMPT =
            "Transcripción en español de un vendedor de coleccionables en un live.";

    private static void writeField(ByteArrayOutputStream out, String boundary, String name, String value)
            throws IOException {
        out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8));
        out.write(value.getBytes(StandardCharsets.UTF_8));
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }
}
