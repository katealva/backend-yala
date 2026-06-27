package com.yala.service;

import com.yala.dto.live.RequestLiveCommentDTO;
import com.yala.dto.live.ResponseLiveCommentDTO;
import com.yala.event.LiveCommentEvent;
import com.yala.exceptions.AuctionNotActiveException;
import com.yala.exceptions.ResourceNotFoundException;
import com.yala.model.LiveComment;
import com.yala.model.LiveStatus;
import com.yala.model.LiveStream;
import com.yala.model.User;
import com.yala.repository.LiveCommentRepository;
import com.yala.repository.LiveStreamRepository;
import com.yala.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Live chat: registered viewers post comments that are broadcast to the live's chat topic. */
@Slf4j
@Service
@RequiredArgsConstructor
public class LiveCommentService {

    private final LiveCommentRepository liveCommentRepository;
    private final LiveStreamRepository liveStreamRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final LiveMapper liveMapper;

    @Transactional
    public ResponseLiveCommentDTO post(Long streamId, RequestLiveCommentDTO request, String userEmail) {
        LiveStream stream = liveStreamRepository.findById(streamId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Live stream not found with id: " + streamId));
        if (stream.getStatus() != LiveStatus.LIVE) {
            throw new AuctionNotActiveException("The live stream has ended");
        }
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        LiveComment comment = liveCommentRepository.save(LiveComment.builder()
                .text(request.text())
                .liveStream(stream)
                .user(user)
                .build());

        eventPublisher.publishEvent(new LiveCommentEvent(comment.getId()));
        return liveMapper.toCommentDto(comment);
    }

    @Transactional(readOnly = true)
    public Page<ResponseLiveCommentDTO> listRecent(Long streamId, Pageable pageable) {
        if (!liveStreamRepository.existsById(streamId)) {
            throw new ResourceNotFoundException("Live stream not found with id: " + streamId);
        }
        return liveCommentRepository.findByLiveStreamIdOrderByCreatedAtDesc(streamId, pageable)
                .map(liveMapper::toCommentDto);
    }
}
