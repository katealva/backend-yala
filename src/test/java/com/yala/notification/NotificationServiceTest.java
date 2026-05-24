package com.yala.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yala.config.ModelMapperConfig;
import com.yala.exceptions.ResourceNotFoundException;
import com.yala.exceptions.UnauthorizedException;
import com.yala.notification.dto.NotificationResponse;
import com.yala.user.Role;
import com.yala.user.User;
import com.yala.user.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private UserRepository userRepository;
    @Mock private SimpMessagingTemplate messagingTemplate;

    @Spy
    private ModelMapper modelMapper = new ModelMapperConfig().modelMapper();

    @InjectMocks
    private NotificationServiceImpl notificationService;

    private User sampleUser() {
        return User.builder().id(1L).name("Ada").email("ada@yala.pe").role(Role.USER).build();
    }

    private Notification sampleNotification(Long id, User user) {
        return Notification.builder()
                .id(id).type(NotificationType.NEW_BID).message("You have a new bid")
                .isRead(false).user(user).build();
    }

    @Test
    void shouldCreateNotificationWhenRecipientExists() {
        User user = sampleUser();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> {
            Notification n = inv.getArgument(0);
            n.setId(99L);
            return n;
        });

        NotificationResponse response = notificationService.createNotification(
                1L, NotificationType.BID_OUTBID, "You have been outbid! Current price: 300");

        assertThat(response.id()).isEqualTo(99L);
        assertThat(response.type()).isEqualTo("BID_OUTBID");
        assertThat(response.isRead()).isFalse();
        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    void shouldThrowResourceNotFoundExceptionWhenRecipientDoesNotExist() {
        when(userRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.createNotification(
                404L, NotificationType.NEW_BID, "Hello"))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void shouldReturnPagedNotificationsWhenFindMineInvoked() {
        User user = sampleUser();
        when(userRepository.findByEmail("ada@yala.pe")).thenReturn(Optional.of(user));
        when(notificationRepository.findByUserId(eq(1L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sampleNotification(1L, user))));

        Page<NotificationResponse> result = notificationService.findMine(
                "ada@yala.pe", PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).type()).isEqualTo("NEW_BID");
    }

    @Test
    void shouldMarkNotificationAsReadWhenInvokedByOwner() {
        User user = sampleUser();
        Notification n = sampleNotification(50L, user);
        when(notificationRepository.findById(50L)).thenReturn(Optional.of(n));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        NotificationResponse response = notificationService.markAsRead(50L, "ada@yala.pe");

        assertThat(response.isRead()).isTrue();
        assertThat(n.getIsRead()).isTrue();
    }

    @Test
    void shouldThrowUnauthorizedExceptionWhenMarkingAsReadByNonOwner() {
        User user = sampleUser();
        Notification n = sampleNotification(50L, user);
        when(notificationRepository.findById(50L)).thenReturn(Optional.of(n));

        assertThatThrownBy(() -> notificationService.markAsRead(50L, "intruder@yala.pe"))
                .isInstanceOf(UnauthorizedException.class);
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void shouldThrowResourceNotFoundExceptionWhenMarkingAsReadMissingNotification() {
        when(notificationRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markAsRead(404L, "ada@yala.pe"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void shouldReturnUpdatedCountWhenMarkAllAsReadInvoked() {
        when(userRepository.findByEmail("ada@yala.pe")).thenReturn(Optional.of(sampleUser()));
        when(notificationRepository.markAllAsReadByUserId(1L)).thenReturn(5);

        int updated = notificationService.markAllAsRead("ada@yala.pe");

        assertThat(updated).isEqualTo(5);
    }

    @Test
    void shouldReturnUnreadCountWhenInvoked() {
        when(userRepository.findByEmail("ada@yala.pe")).thenReturn(Optional.of(sampleUser()));
        when(notificationRepository.countByUserIdAndIsReadFalse(1L)).thenReturn(3L);

        long unread = notificationService.countUnread("ada@yala.pe");

        assertThat(unread).isEqualTo(3L);
    }

    // helper to keep imports clean for eq matcher above
    private static <T> T eq(T value) {
        return org.mockito.ArgumentMatchers.eq(value);
    }
}
