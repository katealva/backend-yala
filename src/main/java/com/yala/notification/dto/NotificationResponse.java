package com.yala.notification.dto;

import com.yala.notification.Notification;
import java.time.LocalDateTime;

public record NotificationResponse(
        Long id,
        String type,
        String message,
        Boolean isRead,
        LocalDateTime createdAt) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType() != null ? notification.getType().name() : null,
                notification.getMessage(),
                notification.getIsRead(),
                notification.getCreatedAt());
    }
}
