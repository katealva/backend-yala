package com.yala.notification;
import com.yala.model.*;

import com.yala.notification.dto.NotificationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface NotificationService {

    /**
     * Internal entry point invoked by event listeners. Persists a notification
     * targeted at the given user.
     */
    NotificationResponse createNotification(Long userId, NotificationType type, String message);

    Page<NotificationResponse> findMine(String userEmail, Pageable pageable);

    NotificationResponse markAsRead(Long id, String userEmail);

    int markAllAsRead(String userEmail);

    long countUnread(String userEmail);
}
