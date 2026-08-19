package com.manuelorg.cross_pesa.notification;

import com.manuelorg.cross_pesa.auth.entity.User;
import com.manuelorg.cross_pesa.auth.repository.UserRepository;
import com.manuelorg.cross_pesa.notification.dto.NotificationResponse;
import com.manuelorg.cross_pesa.notification.dto.TriggerNotificationEvent;
import com.manuelorg.cross_pesa.notification.entity.Notification;
import com.manuelorg.cross_pesa.notification.enums.NotificationStatus;
import com.manuelorg.cross_pesa.notification.enums.NotificationType;
import com.manuelorg.cross_pesa.notification.repository.NotificationRepository;
import com.manuelorg.cross_pesa.notification.service.NotificationService;
import com.manuelorg.cross_pesa.transaction.entity.Transaction;
import com.manuelorg.cross_pesa.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private NotificationService notificationService;

    private User user;
    private UUID userId;
    private UUID transactionId;
    private Transaction transaction;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        transactionId = UUID.randomUUID();

        user = User.builder()
                .id(userId)
                .email("test@example.com")
                .phoneNumber("+254700000000")
                .firstName("Test")
                .lastName("User")
                .build();

        transaction = Transaction.builder()
                .id(transactionId)
                .build();
    }

    @Test
    void handleNotificationEvent_NewEvent_WithTransaction_SavesNotification() {
        TriggerNotificationEvent event = new TriggerNotificationEvent(
                userId,
                transactionId,
                "Transfer Successful",
                "Your transfer of $100 succeeded",
                NotificationType.IN_APP,
                Map.of("amount", 100)
        );

        when(notificationRepository.findByIdempotencyKey(any(UUID.class))).thenReturn(Optional.empty());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(transaction));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification n = invocation.getArgument(0);
            n.setId(UUID.randomUUID());
            return n;
        });

        notificationService.handleNotificationEvent(event);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        Notification saved = captor.getValue();

        assertEquals(user, saved.getUser());
        assertEquals(transaction, saved.getTransaction());
        assertEquals("Transfer Successful", saved.getTitle());
        assertEquals("Your transfer of $100 succeeded", saved.getMessage());
        assertEquals(NotificationType.IN_APP, saved.getNotificationType());
        assertEquals(NotificationStatus.UNREAD, saved.getStatus());
        assertEquals(0, saved.getRetryCount());
        assertNotNull(saved.getIdempotencyKey());
    }

    @Test
    void handleNotificationEvent_AlreadyProcessed_SkipsExecution() {
        TriggerNotificationEvent event = new TriggerNotificationEvent(
                userId,
                transactionId,
                "Transfer Successful",
                "Your transfer of $100 succeeded",
                NotificationType.IN_APP,
                Map.of()
        );

        when(notificationRepository.findByIdempotencyKey(any(UUID.class)))
                .thenReturn(Optional.of(Notification.builder().build()));

        notificationService.handleNotificationEvent(event);

        verify(userRepository, never()).findById(any());
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void handleNotificationEvent_UserNotFound_ThrowsException() {
        TriggerNotificationEvent event = new TriggerNotificationEvent(
                userId,
                null,
                "Welcome",
                "Welcome to CrossPesa",
                NotificationType.IN_APP,
                Map.of()
        );

        when(notificationRepository.findByIdempotencyKey(any(UUID.class))).thenReturn(Optional.empty());
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> notificationService.handleNotificationEvent(event));

        assertTrue(ex.getMessage().contains("User not found"));
    }

    @Test
    void recordDispatchFailure_UpdatesRetryCountAndErrorMessage() {
        UUID notificationId = UUID.randomUUID();
        Notification notification = Notification.builder()
                .id(notificationId)
                .retryCount(1)
                .errorMessage(null)
                .build();

        when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(notification));

        notificationService.recordDispatchFailure(notificationId, "Connection timeout");

        assertEquals(2, notification.getRetryCount());
        assertEquals("Connection timeout", notification.getErrorMessage());
        verify(notificationRepository).save(notification);
    }

    @Test
    void recordDispatchFailure_NullInitialRetryCount_IncrementsToOne() {
        UUID notificationId = UUID.randomUUID();
        Notification notification = Notification.builder()
                .id(notificationId)
                .retryCount(null)
                .errorMessage(null)
                .build();

        when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(notification));

        notificationService.recordDispatchFailure(notificationId, "Gateway error");

        assertEquals(1, notification.getRetryCount());
        assertEquals("Gateway error", notification.getErrorMessage());
        verify(notificationRepository).save(notification);
    }

    @Test
    void getUserDashboardNotifications_ReturnsPagedNotifications() {
        Pageable pageable = PageRequest.of(0, 10);
        Notification notification = Notification.builder()
                .id(UUID.randomUUID())
                .title("Alert")
                .message("Message")
                .notificationType(NotificationType.IN_APP)
                .status(NotificationStatus.UNREAD)
                .createdAt(OffsetDateTime.now())
                .build();

        Page<Notification> page = new PageImpl<>(List.of(notification));
        when(notificationRepository.findAllByUserIdOrderByCreatedAtDesc(userId, pageable)).thenReturn(page);

        Page<NotificationResponse> result = notificationService.getUserDashboardNotifications(userId, pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("Alert", result.getContent().getFirst().title());
    }

    @Test
    void markAsRead_Success() {
        UUID notificationId = UUID.randomUUID();
        Notification notification = Notification.builder()
                .id(notificationId)
                .user(user)
                .status(NotificationStatus.UNREAD)
                .build();

        when(notificationRepository.findByIdAndUserId(notificationId, userId))
                .thenReturn(Optional.of(notification));

        notificationService.markAsRead(notificationId, userId);

        assertEquals(NotificationStatus.READ, notification.getStatus());
        verify(notificationRepository).save(notification);
    }

    @Test
    void markAsRead_NotFoundOrUnauthorized_ThrowsException() {
        UUID notificationId = UUID.randomUUID();
        when(notificationRepository.findByIdAndUserId(notificationId, userId))
                .thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> notificationService.markAsRead(notificationId, userId));

        assertTrue(ex.getMessage().contains("Notification not found or unauthorized"));
        verify(notificationRepository, never()).save(any());
    }
}
