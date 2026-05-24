package com.yala.controller;
import com.yala.service.*;
import com.yala.repository.*;
import com.yala.model.*;

import com.yala.notification.dto.NotificationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "Bandeja de notificaciones del usuario autenticado")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @Operation(summary = "Lista paginada de notificaciones del usuario autenticado")
    public ResponseEntity<Page<NotificationResponse>> findMine(
            Authentication auth,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
                    Pageable pageable) {
        return ResponseEntity.ok(notificationService.findMine(auth.getName(), pageable));
    }

    @PutMapping("/{id}/read")
    @Operation(summary = "Marca una notificación como leída")
    public ResponseEntity<NotificationResponse> markAsRead(
            @PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok(notificationService.markAsRead(id, auth.getName()));
    }

    @PutMapping("/read-all")
    @Operation(summary = "Marca todas las notificaciones del usuario como leídas")
    public ResponseEntity<Map<String, Integer>> markAllAsRead(Authentication auth) {
        int updated = notificationService.markAllAsRead(auth.getName());
        return ResponseEntity.ok(Map.of("updated", updated));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Cantidad de notificaciones sin leer del usuario")
    public ResponseEntity<Map<String, Long>> countUnread(Authentication auth) {
        return ResponseEntity.ok(
                Map.of("unread", notificationService.countUnread(auth.getName())));
    }
}
