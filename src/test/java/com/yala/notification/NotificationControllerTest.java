package com.yala.notification;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yala.auth.JwtService;
import com.yala.exception.ResourceNotFoundException;
import com.yala.exception.UnauthorizedException;
import com.yala.notification.dto.NotificationResponse;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(NotificationController.class)
@AutoConfigureMockMvc(addFilters = false)
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService notificationService;

    @MockitoBean
    private JwtService jwtService;

    private static Principal principal(String email) {
        return new UsernamePasswordAuthenticationToken(
                email, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    private NotificationResponse sample(Long id, boolean isRead) {
        return new NotificationResponse(
                id, "NEW_BID", "You have a new bid", isRead, LocalDateTime.now());
    }

    @Test
    void shouldReturn200WithNotificationsWhenFindMineInvoked() throws Exception {
        when(notificationService.findMine(eq("ada@yala.pe"), any()))
                .thenReturn(new PageImpl<>(
                        List.of(sample(1L, false), sample(2L, true)),
                        PageRequest.of(0, 20), 2));

        mockMvc.perform(get("/api/v1/notifications").principal(principal("ada@yala.pe")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1L))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void shouldReturn200WhenMarkAsReadInvokedByOwner() throws Exception {
        when(notificationService.markAsRead(eq(50L), eq("ada@yala.pe")))
                .thenReturn(sample(50L, true));

        mockMvc.perform(put("/api/v1/notifications/50/read").principal(principal("ada@yala.pe")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isRead").value(true));
    }

    @Test
    void shouldReturn401WhenMarkAsReadInvokedByNonOwner() throws Exception {
        when(notificationService.markAsRead(eq(50L), eq("intruder@yala.pe")))
                .thenThrow(new UnauthorizedException("You can only mark your own notifications as read"));

        mockMvc.perform(put("/api/v1/notifications/50/read").principal(principal("intruder@yala.pe")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturn404WhenMarkAsReadInvokedOnMissingNotification() throws Exception {
        when(notificationService.markAsRead(eq(404L), eq("ada@yala.pe")))
                .thenThrow(new ResourceNotFoundException("Notification not found with id: 404"));

        mockMvc.perform(put("/api/v1/notifications/404/read").principal(principal("ada@yala.pe")))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturn200WithUpdatedCountWhenMarkAllAsReadInvoked() throws Exception {
        when(notificationService.markAllAsRead("ada@yala.pe")).thenReturn(7);

        mockMvc.perform(put("/api/v1/notifications/read-all").principal(principal("ada@yala.pe")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updated").value(7));
    }

    @Test
    void shouldReturn200WithUnreadCountWhenInvoked() throws Exception {
        when(notificationService.countUnread("ada@yala.pe")).thenReturn(3L);

        mockMvc.perform(get("/api/v1/notifications/unread-count").principal(principal("ada@yala.pe")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unread").value(3));
    }
}
