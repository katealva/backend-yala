package com.yala.service;

import com.yala.dto.live.RequestStartLiveDTO;
import com.yala.dto.live.ResponseLiveAuctionDTO;
import com.yala.dto.live.ResponseLiveStreamDTO;
import com.yala.dto.live.ResponseLiveSummaryDTO;
import com.yala.dto.live.ResponseLiveTokenDTO;
import com.yala.event.LiveEndedEvent;
import com.yala.exceptions.ResourceNotFoundException;
import com.yala.exceptions.UnauthorizedException;
import com.yala.model.LiveAuction;
import com.yala.model.LiveAuctionStatus;
import com.yala.model.LiveStatus;
import com.yala.model.LiveStream;
import com.yala.model.Role;
import com.yala.model.User;
import com.yala.repository.LiveAuctionRepository;
import com.yala.repository.LiveBidRepository;
import com.yala.repository.LiveStreamRepository;
import com.yala.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Live stream lifecycle: a verified seller starts a broadcast (provisioning a LiveKit
 * room + publisher token), viewers obtain subscribe-only tokens, and the seller ends it
 * (closing any open flash auction and tearing down the room).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LiveStreamService {

    private final LiveStreamRepository liveStreamRepository;
    private final LiveAuctionRepository liveAuctionRepository;
    private final LiveBidRepository liveBidRepository;
    private final UserRepository userRepository;
    private final LiveKitService liveKitService;
    private final LiveAuctionService liveAuctionService;
    private final LiveMapper liveMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public ResponseLiveTokenDTO start(RequestStartLiveDTO request, String sellerEmail) {
        User seller = userRepository.findByEmail(sellerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        boolean canStream = seller.getRole() == Role.ADMIN
                || (seller.getRole() == Role.SELLER && Boolean.TRUE.equals(seller.getIsVerifiedSeller()));
        if (!canStream) {
            throw new UnauthorizedException(
                    "Only verified sellers can start a live stream");
        }

        String roomName = "live-" + UUID.randomUUID();
        LiveStream stream = liveStreamRepository.save(LiveStream.builder()
                .title(request.title())
                .coverImageUrl(request.coverImageUrl())
                .status(LiveStatus.LIVE)
                .roomName(roomName)
                .seller(seller)
                .build());

        String token = liveKitService.publisherToken(
                roomName, "seller-" + seller.getId(), seller.getName());

        log.info("Live {} started by seller {} (room {})",
                stream.getId(), seller.getEmail(), roomName);
        return new ResponseLiveTokenDTO(stream.getId(), roomName, liveKitService.getUrl(), token);
    }

    @Transactional
    public void end(Long streamId, String sellerEmail) {
        LiveStream stream = findOrThrow(streamId);
        if (!stream.getSeller().getEmail().equals(sellerEmail)) {
            throw new UnauthorizedException("Only the host can end this live stream");
        }
        endStream(stream);
        log.info("Live {} ended by seller {}", stream.getId(), sellerEmail);
    }

    /**
     * Handles LiveKit webhooks. On {@code room_finished} (the seller's connection dropped or
     * the room was torn down) the matching live is ended. Signature is verified by
     * {@link LiveKitService}; unverifiable or irrelevant events are ignored.
     */
    @Transactional
    public void handleWebhook(String body, String authHeader) {
        liveKitService.parseWebhook(body, authHeader).ifPresent(info -> {
            if ("room_finished".equals(info.event()) && info.roomName() != null) {
                liveStreamRepository.findByRoomName(info.roomName()).ifPresent(stream -> {
                    if (stream.getStatus() == LiveStatus.LIVE) {
                        endStream(stream);
                        log.info("Live {} ended via LiveKit webhook (room_finished)", stream.getId());
                    }
                });
            }
        });
    }

    /** Ends a live: closes any open flash auction, marks ENDED, tears down the room. */
    private void endStream(LiveStream stream) {
        if (stream.getStatus() == LiveStatus.ENDED) {
            return;
        }
        // Close any still-open flash auction before ending the broadcast.
        liveAuctionRepository
                .findFirstByLiveStreamIdAndStatusOrderByStartedAtDesc(
                        stream.getId(), LiveAuctionStatus.ACTIVE)
                .ifPresent(active -> liveAuctionService.close(
                        active.getId(), stream.getSeller().getEmail()));

        stream.setStatus(LiveStatus.ENDED);
        stream.setEndedAt(LocalDateTime.now());
        liveStreamRepository.save(stream);

        liveKitService.deleteRoom(stream.getRoomName());
        eventPublisher.publishEvent(new LiveEndedEvent(stream.getId()));
    }

    @Transactional(readOnly = true)
    public Page<ResponseLiveSummaryDTO> listActive(Pageable pageable) {
        return liveStreamRepository.findByStatus(LiveStatus.LIVE, pageable)
                .map(liveMapper::toSummary);
    }

    @Transactional(readOnly = true)
    public ResponseLiveStreamDTO findById(Long id) {
        LiveStream stream = findOrThrow(id);
        ResponseLiveAuctionDTO active = liveAuctionRepository
                .findFirstByLiveStreamIdAndStatusOrderByStartedAtDesc(id, LiveAuctionStatus.ACTIVE)
                .map(this::toAuctionDto)
                .orElse(null);
        return liveMapper.toStreamDetail(stream, active);
    }

    @Transactional(readOnly = true)
    public ResponseLiveTokenDTO watchToken(Long streamId, String viewerEmail) {
        LiveStream stream = findOrThrow(streamId);
        if (stream.getStatus() != LiveStatus.LIVE) {
            throw new ResourceNotFoundException("Live stream " + streamId + " is not currently live");
        }

        String identity;
        String name;
        if (viewerEmail != null) {
            User user = userRepository.findByEmail(viewerEmail)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found"));
            identity = "user-" + user.getId();
            name = user.getName();
        } else {
            identity = "anon-" + UUID.randomUUID();
            name = "Invitado";
        }

        String token = liveKitService.viewerToken(stream.getRoomName(), identity, name);
        return new ResponseLiveTokenDTO(
                stream.getId(), stream.getRoomName(), liveKitService.getUrl(), token);
    }

    private ResponseLiveAuctionDTO toAuctionDto(LiveAuction a) {
        return liveMapper.toAuctionDto(a, liveBidRepository.countByLiveAuctionId(a.getId()));
    }

    private LiveStream findOrThrow(Long id) {
        return liveStreamRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Live stream not found with id: " + id));
    }
}
