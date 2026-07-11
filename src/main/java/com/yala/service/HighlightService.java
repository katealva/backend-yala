package com.yala.service;

import com.yala.model.LiveAuction;
import com.yala.model.LiveAuctionStatus;
import com.yala.model.LiveBid;
import com.yala.model.LiveClip;
import com.yala.model.LiveClipStatus;
import com.yala.model.LiveComment;
import com.yala.model.LiveStream;
import com.yala.model.NotificationType;
import com.yala.dto.live.ResponseLiveClipDTO;
import com.yala.exceptions.ResourceNotFoundException;
import com.yala.exceptions.UnauthorizedException;
import com.yala.repository.LiveAuctionRepository;
import com.yala.repository.LiveBidRepository;
import com.yala.repository.LiveClipRepository;
import com.yala.repository.LiveCommentRepository;
import com.yala.repository.LiveStreamRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Generates highlight clips from a finished live's recording (ADR-001, Fase 1). Combines
 * signal heuristics (flash-auction closes, bid bursts, chat velocity — all timestamped) with
 * an LLM (see {@link HighlightAiService}) to pick and title 3-5 iconic moments, then cuts each
 * window out of the recorded MP4 with ffmpeg (16:9 for the MVP) and stores it in S3.
 *
 * <p>Every external step degrades gracefully: without OpenAI it falls back to the signal
 * heuristics; without a recording or ffmpeg it still persists the chosen moments as PENDING
 * clips (metadata only) so the seller sees what would be produced once the infra is enabled.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HighlightService {

    private static final int MAX_CLIPS = 5;
    private static final long PRE_ROLL_MS = 6_000L;
    private static final long POST_ROLL_MS = 18_000L;
    private static final long MAX_CLIP_MS = 60_000L;
    private static final long WINDOW_MS = 25_000L;
    private static final int MIN_BIDS_BURST = 4;
    private static final int MIN_COMMENTS_BURST = 6;
    private static final long DEDUP_MS = 15_000L;

    private final LiveStreamRepository liveStreamRepository;
    private final LiveClipRepository liveClipRepository;
    private final LiveAuctionRepository liveAuctionRepository;
    private final LiveBidRepository liveBidRepository;
    private final LiveCommentRepository liveCommentRepository;
    private final HighlightAiService aiService;
    private final NotificationService notificationService;
    private final S3Client s3Client;

    @Value("${aws.s3.bucket:}")
    private String bucket;

    @Value("${aws.region:us-east-1}")
    private String region;

    /** A chosen highlight window, ready to be cut. */
    private record Moment(long anchorMs, double score, String title, String caption, String reason) {
    }

    /** Clips of a live for its host (seller-owned; used by the "Clips de tu live" panel). */
    @Transactional(readOnly = true)
    public List<ResponseLiveClipDTO> listClips(Long streamId, String sellerEmail) {
        LiveStream stream = liveStreamRepository.findById(streamId)
                .orElseThrow(() -> new ResourceNotFoundException("Live stream not found with id: " + streamId));
        if (!stream.getSeller().getEmail().equals(sellerEmail)) {
            throw new UnauthorizedException("Only the host can see this live's clips");
        }
        return liveClipRepository.findByLiveStreamIdOrderByScoreDesc(streamId)
                .stream().map(ResponseLiveClipDTO::from).toList();
    }

    /**
     * Runs the full pipeline for a finished live, in the background. Triggered from the
     * egress_ended webhook once the recording is in S3.
     */
    @Async
    @Transactional
    public void generateForLive(Long streamId) {
        LiveStream stream = liveStreamRepository.findById(streamId).orElse(null);
        if (stream == null) {
            return;
        }
        try {
            long durationMs = liveDurationMs(stream);
            List<Moment> moments = selectMoments(stream, durationMs);
            if (moments.isEmpty()) {
                log.info("No highlight moments found for live {}", streamId);
                stream.setRecordingStatus(LiveClipStatus.READY);
                liveStreamRepository.save(stream);
                return;
            }

            boolean canCut = canCutVideo(stream);
            Path recording = canCut ? downloadRecording(stream) : null;

            int ready = 0;
            try {
                for (Moment m : moments) {
                    long start = Math.max(0, m.anchorMs() - PRE_ROLL_MS);
                    long end = Math.min(durationMs, Math.min(m.anchorMs() + POST_ROLL_MS, start + MAX_CLIP_MS));
                    if (end <= start) {
                        continue;
                    }
                    LiveClip clip = LiveClip.builder()
                            .liveStream(stream)
                            .title(m.title())
                            .caption(m.caption())
                            .reason(m.reason())
                            .startMs(start)
                            .endMs(end)
                            .score(m.score())
                            .format("16:9")
                            .status(LiveClipStatus.PENDING)
                            .build();
                    if (recording != null) {
                        String url = cutAndUpload(stream, recording, start, end);
                        if (url != null) {
                            clip.setUrl(url);
                            clip.setStatus(LiveClipStatus.READY);
                            ready++;
                        }
                    }
                    liveClipRepository.save(clip);
                }
            } finally {
                if (recording != null) {
                    try {
                        Files.deleteIfExists(recording);
                    } catch (Exception ignore) {
                        // temp cleanup best-effort
                    }
                }
            }

            stream.setRecordingStatus(ready > 0 ? LiveClipStatus.READY : LiveClipStatus.PENDING);
            liveStreamRepository.save(stream);

            if (ready > 0) {
                notificationService.createNotification(
                        stream.getSeller().getId(),
                        NotificationType.CLIP_READY,
                        "Tus clips del live \"" + stream.getTitle() + "\" están listos para descargar.");
            }
            log.info("Highlights for live {}: {} moments, {} clips ready", streamId, moments.size(), ready);
        } catch (Exception e) {
            log.error("Highlight generation failed for live {}: {}", streamId, e.getMessage(), e);
            stream.setRecordingStatus(LiveClipStatus.FAILED);
            liveStreamRepository.save(stream);
        }
    }

    // ── Moment selection ────────────────────────────────────────────────────────

    private List<Moment> selectMoments(LiveStream stream, long durationMs) {
        // AI-first (ADR-001): let the LLM read the whole timeline and pick/title moments.
        String timeline = buildTimeline(stream, durationMs);
        List<HighlightAiService.AiClip> ai = aiService.selectClips(timeline, durationMs);
        if (!ai.isEmpty()) {
            List<Moment> out = new ArrayList<>();
            for (HighlightAiService.AiClip c : ai) {
                long anchor = (c.startMs() + c.endMs()) / 2;
                out.add(new Moment(anchor, 100, c.title(), c.caption(), c.reason()));
            }
            return out.subList(0, Math.min(out.size(), MAX_CLIPS));
        }
        // Fallback: pure signal heuristics (also used when OpenAI isn't configured).
        return signalMoments(stream, durationMs);
    }

    private List<Moment> signalMoments(LiveStream stream, long durationMs) {
        List<Moment> candidates = new ArrayList<>();

        // 1) Every flash auction closed with a winner is a guaranteed "¡vendido!" climax.
        for (LiveAuction a : liveAuctionRepository.findByLiveStreamId(stream.getId())) {
            if (a.getStatus() == LiveAuctionStatus.SOLD && a.getEndedAt() != null) {
                long anchor = offsetMs(stream, a.getEndedAt());
                long bids = liveBidRepository.countByLiveAuctionId(a.getId());
                float price = a.getWinningAmount() != null ? a.getWinningAmount()
                        : (a.getCurrentPrice() != null ? a.getCurrentPrice() : 0f);
                double score = 100 + bids * 5 + price / 10.0;
                candidates.add(new Moment(anchor, score,
                        "¡Vendido! " + a.getTitle(),
                        a.getTitle() + " se vendió a S/. " + fmt(price) + " en vivo 🔥 #Yala #Subastas",
                        "Cierre de subasta con ganador (" + bids + " pujas)"));
            }
        }

        // 2) Bid bursts: 25s windows with several bids (a bidding war).
        List<LiveBid> bids = liveBidRepository.findByLiveAuction_LiveStream_IdOrderByPlacedAtAsc(stream.getId());
        for (long[] w : bucketBursts(offsets(stream, bids.stream().map(LiveBid::getPlacedAt).toList()), MIN_BIDS_BURST)) {
            candidates.add(new Moment(w[0], 40 + w[1] * 4,
                    "Guerra de pujas", "La puja se calentó en vivo 🔥 #Yala #Subastas",
                    w[1] + " pujas en pocos segundos"));
        }

        // 3) Chat spikes: 25s windows with many comments (engagement).
        List<LiveComment> comments = liveCommentRepository.findByLiveStreamIdOrderByCreatedAtAsc(stream.getId());
        for (long[] w : bucketBursts(offsets(stream, comments.stream().map(LiveComment::getCreatedAt).toList()), MIN_COMMENTS_BURST)) {
            candidates.add(new Moment(w[0], 20 + w[1] * 2,
                    "El chat explotó", "La audiencia reaccionó en vivo #Yala #Subastas",
                    w[1] + " comentarios en pocos segundos"));
        }

        // Rank, drop near-duplicates, keep the top few.
        candidates.sort(Comparator.comparingDouble(Moment::score).reversed());
        List<Moment> picked = new ArrayList<>();
        for (Moment m : candidates) {
            boolean near = picked.stream().anyMatch(p -> Math.abs(p.anchorMs() - m.anchorMs()) < DEDUP_MS);
            if (!near) {
                picked.add(m);
            }
            if (picked.size() >= MAX_CLIPS) {
                break;
            }
        }
        return picked;
    }

    /** Groups event offsets into fixed windows and returns [windowCenter, count] for busy ones. */
    private List<long[]> bucketBursts(List<Long> sortedOffsets, int minCount) {
        List<long[]> out = new ArrayList<>();
        if (sortedOffsets.isEmpty()) {
            return out;
        }
        long bucketStart = sortedOffsets.get(0);
        int count = 0;
        for (long off : sortedOffsets) {
            if (off - bucketStart <= WINDOW_MS) {
                count++;
            } else {
                if (count >= minCount) {
                    out.add(new long[] {bucketStart + WINDOW_MS / 2, count});
                }
                bucketStart = off;
                count = 1;
            }
        }
        if (count >= minCount) {
            out.add(new long[] {bucketStart + WINDOW_MS / 2, count});
        }
        return out;
    }

    private String buildTimeline(LiveStream stream, long durationMs) {
        StringBuilder sb = new StringBuilder();
        for (LiveAuction a : liveAuctionRepository.findByLiveStreamId(stream.getId())) {
            long end = a.getEndedAt() != null ? offsetMs(stream, a.getEndedAt()) : -1;
            long bids = liveBidRepository.countByLiveAuctionId(a.getId());
            sb.append("[SUBASTA @").append(end).append("ms] \"").append(a.getTitle())
                    .append("\" estado=").append(a.getStatus())
                    .append(" pujas=").append(bids)
                    .append(" precioFinal=S/.").append(fmt(a.getWinningAmount() != null ? a.getWinningAmount()
                            : (a.getCurrentPrice() != null ? a.getCurrentPrice() : 0f)))
                    .append('\n');
        }
        List<LiveComment> comments = liveCommentRepository.findByLiveStreamIdOrderByCreatedAtAsc(stream.getId());
        int shown = 0;
        for (LiveComment c : comments) {
            if (shown++ >= 120) {
                break;
            }
            String author = c.getUser() != null ? c.getUser().getName() : "Anónimo";
            sb.append("[CHAT @").append(offsetMs(stream, c.getCreatedAt())).append("ms] ")
                    .append(author).append(": ").append(c.getText()).append('\n');
        }
        return sb.toString();
    }

    // ── Video cutting (ffmpeg + S3) ─────────────────────────────────────────────

    private boolean canCutVideo(LiveStream stream) {
        return stream.getRecordingKey() != null && !stream.getRecordingKey().isBlank()
                && bucket != null && !bucket.isBlank() && ffmpegAvailable();
    }

    private Path downloadRecording(LiveStream stream) throws Exception {
        Path tmp = Files.createTempFile("live-" + stream.getId() + "-", ".mp4");
        // El AWS SDK v2 (ResponseTransformer.toFile) NO sobrescribe un archivo existente y falla con
        // "Failed to read response into file". createTempFile ya creó el archivo vacío, así que lo
        // borramos para que getObject pueda crearlo y escribir la grabación.
        Files.deleteIfExists(tmp);
        s3Client.getObject(
                GetObjectRequest.builder().bucket(bucket).key(stream.getRecordingKey()).build(),
                tmp);
        return tmp;
    }

    private String cutAndUpload(LiveStream stream, Path recording, long startMs, long endMs) {
        try {
            Path out = Files.createTempFile("clip-", ".mp4");
            double startSec = startMs / 1000.0;
            double durSec = (endMs - startMs) / 1000.0;
            Process p = new ProcessBuilder(
                    "ffmpeg", "-y",
                    "-ss", String.valueOf(startSec),
                    "-i", recording.toString(),
                    "-t", String.valueOf(durSec),
                    "-c:v", "libx264", "-preset", "veryfast",
                    "-c:a", "aac", "-movflags", "+faststart",
                    out.toString())
                    .redirectErrorStream(true)
                    .start();
            // Drain output so the process doesn't block on a full pipe.
            p.getInputStream().readAllBytes();
            int code = p.waitFor();
            if (code != 0 || !Files.exists(out) || Files.size(out) == 0) {
                log.warn("ffmpeg failed (exit {}) for live {} clip {}-{}", code, stream.getId(), startMs, endMs);
                Files.deleteIfExists(out);
                return null;
            }
            String key = "clips/" + stream.getId() + "/" + UUID.randomUUID() + ".mp4";
            s3Client.putObject(
                    PutObjectRequest.builder().bucket(bucket).key(key).contentType("video/mp4").build(),
                    RequestBody.fromFile(out));
            Files.deleteIfExists(out);
            return "https://" + bucket + ".s3." + region + ".amazonaws.com/" + key;
        } catch (Exception e) {
            log.warn("Could not cut/upload clip for live {}: {}", stream.getId(), e.getMessage());
            return null;
        }
    }

    private boolean ffmpegAvailable() {
        try {
            Process p = new ProcessBuilder("ffmpeg", "-version").redirectErrorStream(true).start();
            p.getInputStream().readAllBytes();
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private long liveDurationMs(LiveStream stream) {
        LocalDateTime start = stream.getStartedAt();
        LocalDateTime end = stream.getEndedAt() != null ? stream.getEndedAt() : LocalDateTime.now();
        if (start == null) {
            return 0;
        }
        return Math.max(0, Duration.between(start, end).toMillis());
    }

    private long offsetMs(LiveStream stream, LocalDateTime when) {
        if (stream.getStartedAt() == null || when == null) {
            return 0;
        }
        return Math.max(0, Duration.between(stream.getStartedAt(), when).toMillis());
    }

    private List<Long> offsets(LiveStream stream, List<LocalDateTime> times) {
        List<Long> out = new ArrayList<>(times.size());
        for (LocalDateTime t : times) {
            out.add(offsetMs(stream, t));
        }
        return out;
    }

    private String fmt(float v) {
        return String.format("%.2f", v);
    }
}
