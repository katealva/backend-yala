package com.yala.service;

import io.livekit.server.AccessToken;
import io.livekit.server.CanPublish;
import io.livekit.server.CanPublishData;
import io.livekit.server.CanSubscribe;
import io.livekit.server.EgressServiceClient;
import io.livekit.server.RoomJoin;
import io.livekit.server.RoomName;
import io.livekit.server.RoomServiceClient;
import io.livekit.server.WebhookReceiver;
import java.util.Optional;
import livekit.LivekitEgress;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Thin wrapper around the LiveKit server SDK. Generates join tokens (publisher for the
 * seller, subscribe-only for viewers), best-effort closes rooms via the RoomServiceClient,
 * and verifies inbound webhooks. Credentials come from {@code LIVEKIT_*} env vars; when they
 * are blank the service degrades gracefully (tokens still generated for local dev, room
 * deletion and webhook verification become no-ops).
 */
@Slf4j
@Service
public class LiveKitService {

    private static final long TOKEN_TTL_MS = 6 * 60 * 60 * 1000L; // 6h

    private final String url;
    private final String apiKey;
    private final String apiSecret;
    // AWS creds are needed by egress itself to upload the recording to S3 (LiveKit's
    // egress workers cannot use the ECS task role — they need explicit keys).
    private final String awsBucket;
    private final String awsRegion;
    private final String awsAccessKey;
    private final String awsSecret;

    public LiveKitService(
            @Value("${livekit.url:}") String url,
            @Value("${livekit.api-key:}") String apiKey,
            @Value("${livekit.api-secret:}") String apiSecret,
            @Value("${aws.s3.bucket:}") String awsBucket,
            @Value("${aws.region:us-east-1}") String awsRegion,
            @Value("${aws.access-key-id:}") String awsAccessKey,
            @Value("${aws.secret-access-key:}") String awsSecret) {
        this.url = url;
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.awsBucket = awsBucket;
        this.awsRegion = awsRegion;
        this.awsAccessKey = awsAccessKey;
        this.awsSecret = awsSecret;
    }

    /** The wss:// URL clients use to connect to the LiveKit SFU. */
    public String getUrl() {
        return url;
    }

    public boolean isConfigured() {
        return notBlank(url) && notBlank(apiKey) && notBlank(apiSecret);
    }

    /** Token for the seller: can publish camera/mic and subscribe. */
    public String publisherToken(String roomName, String identity, String name) {
        return buildToken(roomName, identity, name, true);
    }

    /** Token for a viewer: subscribe-only (cannot publish media). */
    public String viewerToken(String roomName, String identity, String name) {
        return buildToken(roomName, identity, name, false);
    }

    private String buildToken(String roomName, String identity, String name, boolean canPublish) {
        AccessToken token = new AccessToken(apiKey, apiSecret);
        token.setIdentity(identity);
        if (notBlank(name)) {
            token.setName(name);
        }
        token.setTtl(TOKEN_TTL_MS);
        token.addGrants(
                new RoomJoin(true),
                new RoomName(roomName),
                new CanPublish(canPublish),
                new CanSubscribe(true),
                new CanPublishData(true));
        return token.toJwt();
    }

    /** True when egress can upload recordings to S3 (LiveKit + explicit AWS keys present). */
    public boolean recordingAvailable() {
        return isConfigured() && notBlank(awsBucket) && notBlank(awsAccessKey) && notBlank(awsSecret);
    }

    /**
     * Starts a RoomComposite egress that records the live to {@code filepath} (an S3 key)
     * as MP4. Returns the egress id, or empty when recording is not configured / the call
     * fails (graceful degradation — the live still works, just without a recording).
     */
    public Optional<String> startRecordingEgress(String roomName, String filepath) {
        if (!recordingAvailable()) {
            log.info("Egress skipped for room {} (LiveKit/AWS recording not configured)", roomName);
            return Optional.empty();
        }
        try {
            LivekitEgress.S3Upload s3 = LivekitEgress.S3Upload.newBuilder()
                    .setAccessKey(awsAccessKey)
                    .setSecret(awsSecret)
                    .setRegion(awsRegion)
                    .setBucket(awsBucket)
                    .build();
            LivekitEgress.EncodedFileOutput output = LivekitEgress.EncodedFileOutput.newBuilder()
                    .setFileType(LivekitEgress.EncodedFileType.MP4)
                    .setFilepath(filepath)
                    .setS3(s3)
                    .build();
            EgressServiceClient client = EgressServiceClient.createClient(httpUrl(), apiKey, apiSecret);
            var response = client.startRoomCompositeEgress(roomName, output).execute();
            if (response.isSuccessful() && response.body() != null) {
                String egressId = response.body().getEgressId();
                log.info("Egress {} started for room {} -> {}", egressId, roomName, filepath);
                return Optional.ofNullable(egressId);
            }
            log.warn("Egress start for room {} returned HTTP {}", roomName, response.code());
            return Optional.empty();
        } catch (Exception ex) {
            log.warn("Could not start egress for room {}: {}", roomName, ex.getMessage());
            return Optional.empty();
        }
    }

    /** Best-effort stop of a running egress (finalizes the MP4 in S3). */
    public void stopEgress(String egressId) {
        if (!isConfigured() || !notBlank(egressId)) {
            return;
        }
        try {
            EgressServiceClient.createClient(httpUrl(), apiKey, apiSecret).stopEgress(egressId).execute();
            log.info("Egress {} stopped", egressId);
        } catch (Exception ex) {
            log.warn("Could not stop egress {}: {}", egressId, ex.getMessage());
        }
    }

    /** Best-effort room teardown so all participants get disconnected when a live ends. */
    public void deleteRoom(String roomName) {
        if (!isConfigured()) {
            return;
        }
        try {
            RoomServiceClient client = RoomServiceClient.createClient(httpUrl(), apiKey, apiSecret);
            client.deleteRoom(roomName).execute();
            log.info("LiveKit room {} deleted", roomName);
        } catch (Exception ex) {
            log.warn("Could not delete LiveKit room {}: {}", roomName, ex.getMessage());
        }
    }

    /**
     * Verifies an inbound webhook (Authorization header + raw body) and returns the
     * decoded event name and room name. Returns empty when LiveKit is not configured or
     * the signature cannot be verified.
     */
    public Optional<WebhookInfo> parseWebhook(String body, String authHeader) {
        if (!isConfigured()) {
            return Optional.empty();
        }
        try {
            var event = new WebhookReceiver(apiKey, apiSecret).receive(body, authHeader);
            String roomName = event.hasRoom() ? event.getRoom().getName() : null;
            String egressId = null;
            String egressStatus = null;
            if (event.hasEgressInfo()) {
                egressId = event.getEgressInfo().getEgressId();
                egressStatus = event.getEgressInfo().getStatus().name();
            }
            return Optional.of(new WebhookInfo(event.getEvent(), roomName, egressId, egressStatus));
        } catch (Exception ex) {
            log.warn("Invalid LiveKit webhook: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    private String httpUrl() {
        if (url.startsWith("wss://")) {
            return "https://" + url.substring("wss://".length());
        }
        if (url.startsWith("ws://")) {
            return "http://" + url.substring("ws://".length());
        }
        return url;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    /** Minimal projection of a LiveKit webhook event (room + optional egress details). */
    public record WebhookInfo(String event, String roomName, String egressId, String egressStatus) {
    }
}
