package com.yala.notification;

import com.yala.exceptions.ResourceNotFoundException;
import com.yala.exceptions.UnauthorizedException;
import com.yala.notification.dto.NotificationResponse;
import com.yala.user.User;
import com.yala.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ModelMapper modelMapper;

    @Override
    @Transactional
    public NotificationResponse createNotification(
            Long userId, NotificationType type, String message) {
        User recipient = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found with id: " + userId));

        Notification saved = notificationRepository.save(Notification.builder()
                .type(type)
                .message(message)
                .isRead(false)
                .user(recipient)
                .build());

        NotificationResponse response = modelMapper.map(saved, NotificationResponse.class);
        messagingTemplate.convertAndSend("/topic/notifications/" + userId, response);

        log.info("Notification {} of type {} delivered to user {}",
                saved.getId(), type, recipient.getEmail());
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<NotificationResponse> findMine(String userEmail, Pageable pageable) {
        User user = resolveUser(userEmail);
        return notificationRepository.findByUserId(user.getId(), pageable)
                .map(n -> modelMapper.map(n, NotificationResponse.class));
    }

    @Override
    @Transactional
    public NotificationResponse markAsRead(Long id, String userEmail) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Notification not found with id: " + id));
        if (!notification.getUser().getEmail().equals(userEmail)) {
            throw new UnauthorizedException("You can only mark your own notifications as read");
        }
        notification.setIsRead(true);
        return modelMapper.map(notificationRepository.save(notification), NotificationResponse.class);
    }

    @Override
    @Transactional
    public int markAllAsRead(String userEmail) {
        User user = resolveUser(userEmail);
        return notificationRepository.markAllAsReadByUserId(user.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public long countUnread(String userEmail) {
        User user = resolveUser(userEmail);
        return notificationRepository.countByUserIdAndIsReadFalse(user.getId());
    }

    private User resolveUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
