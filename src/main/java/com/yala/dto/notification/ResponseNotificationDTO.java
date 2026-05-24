package com.yala.dto.notification;

import java.time.LocalDateTime;

public record ResponseNotificationDTO(
        Long id,
        String type,
        String message,
        Boolean isRead,
        LocalDateTime createdAt) {
}
