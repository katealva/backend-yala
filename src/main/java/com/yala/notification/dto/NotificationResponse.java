package com.yala.notification.dto;

import java.time.LocalDateTime;

public record NotificationResponse(
        Long id,
        String type,
        String message,
        Boolean isRead,
        LocalDateTime createdAt) {
}
