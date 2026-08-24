package com.manuelorg.cross_pesa.notification;

import com.manuelorg.cross_pesa.auth.entity.User;
import com.manuelorg.cross_pesa.notification.entity.Notification;
import com.manuelorg.cross_pesa.notification.enums.NotificationType;
import com.manuelorg.cross_pesa.notification.repository.NotificationRepository;
import com.manuelorg.cross_pesa.notification.service.NotificationDispatchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationDispatchServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationDispatchService dispatchService;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(UUID.randomUUID())
                .email("test@example.com")
                .build();
    }

    @Test
    void recordDispatchFailure_UpdatesRetryCountAndErrorMessage() {
        UUID notificationId = UUID.randomUUID();
        Notification notification = Notification.builder()
                .id(notificationId)
                .retryCount(1)
                .errorMessage(null)
                .notificationType(NotificationType.SMS)
                .user(user)
                .build();

        when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(notification));

        dispatchService.recordDispatchFailure(notificationId, "Connection timeout");

        assertEquals(2, notification.getRetryCount());
        assertEquals("Connection timeout", notification.getErrorMessage());
    }

    @Test
    void recordDispatchFailure_NullInitialRetryCount_IncrementsToOne() {
        UUID notificationId = UUID.randomUUID();
        Notification notification = Notification.builder()
                .id(notificationId)
                .retryCount(null)
                .errorMessage(null)
                .notificationType(NotificationType.SMS)
                .user(user)
                .build();

        when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(notification));

        dispatchService.recordDispatchFailure(notificationId, "Gateway error");

        assertEquals(1, notification.getRetryCount());
        assertEquals("Gateway error", notification.getErrorMessage());
    }

    @Test
    void markDispatched_SetsDispatchedAt() {
        UUID notificationId = UUID.randomUUID();
        Notification notification = Notification.builder()
                .id(notificationId)
                .dispatchedAt(null)
                .notificationType(NotificationType.IN_APP)
                .user(user)
                .build();

        when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(notification));

        dispatchService.markDispatched(notificationId);

        assertNotNull(notification.getDispatchedAt());
    }
}
