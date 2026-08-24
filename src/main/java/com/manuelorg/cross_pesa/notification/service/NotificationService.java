package com.manuelorg.cross_pesa.notification.service;

import com.manuelorg.cross_pesa.auth.entity.User;
import com.manuelorg.cross_pesa.auth.repository.UserRepository;
import com.manuelorg.cross_pesa.notification.dto.NotificationResponse;
import com.manuelorg.cross_pesa.notification.dto.TriggerNotificationEvent;
import com.manuelorg.cross_pesa.notification.entity.Notification;
import com.manuelorg.cross_pesa.notification.enums.NotificationStatus;
import com.manuelorg.cross_pesa.notification.enums.NotificationType;
import com.manuelorg.cross_pesa.notification.repository.NotificationRepository;
import com.manuelorg.cross_pesa.transaction.entity.Transaction;
import com.manuelorg.cross_pesa.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final NotificationDispatchService notificationDispatchService;

    @EventListener
    @Transactional
    public void handleNotificationEvent(TriggerNotificationEvent event) {
        String idKeySource = (event.transactionId() != null ? event.transactionId().toString() : event.userId().toString())
                + (event.type() != null ? event.type().name() : "");
        UUID idempotencyKey = UUID.nameUUIDFromBytes(idKeySource.getBytes());

        if (notificationRepository.findByIdempotencyKey(idempotencyKey).isPresent()) {
            log.atWarn()
                    .addKeyValue("event", "notification.duplicate")
                    .addKeyValue("idempotencyKey", idempotencyKey)
                    .log("Notification already processed for idempotency key");
            return;
        }

        User user = userRepository.findById(event.userId())
                .orElseThrow(() -> new IllegalArgumentException("User not found for notification"));

        Transaction transaction = null;
        if (event.transactionId() != null) {
            transaction = transactionRepository.findById(event.transactionId()).orElse(null);
        }

        // IN_APP notifications require no external delivery — mark them dispatched up front.
        OffsetDateTime dispatchedAt = event.type() == NotificationType.IN_APP ? OffsetDateTime.now() : null;

        Notification notification = Notification.builder()
                .user(user)
                .transaction(transaction)
                .title(event.title())
                .message(event.message())
                .notificationType(event.type())
                .metadata(event.metadata())
                .idempotencyKey(idempotencyKey)
                .status(NotificationStatus.UNREAD)
                .retryCount(0)
                .dispatchedAt(dispatchedAt)
                .build();

        Notification savedNotification = notificationRepository.save(notification);

        // Cross-bean call so the @Async proxy is honoured — external provider
        // HTTP runs on the async executor, not inside this DB transaction.
        if (savedNotification.getDispatchedAt() == null) {
            notificationDispatchService.dispatchExternal(savedNotification.getId());
        }
    }

    /**
     * Synchronous dispatch entrypoint used by the {@code NotificationPoller}.
     */
    public void dispatch(UUID notificationId) {
        notificationDispatchService.dispatch(notificationId);
    }

    // --- Dashboard UI Methods ---

    @Transactional(readOnly = true)
    public Page<NotificationResponse> getUserDashboardNotifications(UUID userId, Pageable pageable) {
        return notificationRepository.findAllByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(this::mapToResponse);
    }

    @Transactional
    public void markAsRead(UUID notificationId, UUID userId) {
        Notification notification = notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found or unauthorized"));

        notification.setStatus(NotificationStatus.READ);
        notificationRepository.save(notification);
    }

    private NotificationResponse mapToResponse(Notification n) {
        return new NotificationResponse(
                n.getId(),
                n.getTitle(),
                n.getMessage(),
                n.getNotificationType(),
                n.getStatus(),
                n.getMetadata(),
                n.getCreatedAt()
        );
    }
}
