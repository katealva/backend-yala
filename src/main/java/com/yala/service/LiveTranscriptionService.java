package com.yala.service;

import com.yala.dto.live.ResponseTranscriptDTO;
import com.yala.exceptions.ResourceNotFoundException;
import com.yala.exceptions.UnauthorizedException;
import com.yala.model.LiveStream;
import com.yala.repository.LiveStreamRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
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
            byte[] bytes = audio.getBytes();
            String filename = audio.getOriginalFilename() != null && !audio.getOriginalFilename().isBlank()
                    ? audio.getOriginalFilename() : "chunk.wav";
            ByteArrayResource file = new ByteArrayResource(bytes) {
                @Override
                public String getFilename() {
                    return filename;
                }
            };
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", file);
            body.add("model", model);
            body.add("language", "es");
            body.add("response_format", "text");

            String text = restClient.post()
                    .uri(baseUrl + "/v1/audio/transcriptions")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            return new ResponseTranscriptDTO(text != null ? text.trim() : "");
        } catch (Exception e) {
            log.warn("Live transcription failed: {}", e.getMessage());
            return new ResponseTranscriptDTO("");
        }
    }
}
