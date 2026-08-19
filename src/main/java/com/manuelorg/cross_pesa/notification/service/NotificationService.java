package com.manuelorg.cross_pesa.notification.service;

import com.africastalking.AfricasTalking;
import com.africastalking.SmsService;
import com.africastalking.sms.Recipient;
import com.manuelorg.cross_pesa.auth.entity.User;
import com.manuelorg.cross_pesa.auth.repository.UserRepository;
import com.manuelorg.cross_pesa.notification.dto.NotificationResponse;
import com.manuelorg.cross_pesa.notification.dto.TriggerNotificationEvent;
import com.manuelorg.cross_pesa.notification.entity.Notification;
import com.manuelorg.cross_pesa.notification.enums.NotificationStatus;
import com.manuelorg.cross_pesa.notification.repository.NotificationRepository;
import com.manuelorg.cross_pesa.transaction.entity.Transaction;
import com.manuelorg.cross_pesa.transaction.repository.TransactionRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;

    // Pull credentials from application.yml / .env
    @Value("${africastalking.username}")
    private String atUsername;

    @Value("${africastalking.api-key}")
    private String atApiKey;

    /**
     * Initializes the Africa's Talking client once when the application starts.
     */
    @PostConstruct
    public void initAfricasTalking() {
        AfricasTalking.initialize(atUsername, atApiKey);
        log.info("Africa's Talking initialized for environment: {}", atUsername);
    }

    @EventListener
    @Transactional
    public void handleNotificationEvent(TriggerNotificationEvent event) {
        String idKeySource = (event.transactionId() != null ? event.transactionId().toString() : event.userId().toString())
                + (event.type() != null ? event.type().name() : "");
        UUID idempotencyKey = UUID.nameUUIDFromBytes(idKeySource.getBytes());

        if (notificationRepository.findByIdempotencyKey(idempotencyKey).isPresent()) {
            log.warn("Notification already processed for key: {}", idempotencyKey);
            return;
        }

        User user = userRepository.findById(event.userId())
                .orElseThrow(() -> new IllegalArgumentException("User not found for notification"));

        Transaction transaction = null;
        if (event.transactionId() != null) {
            transaction = transactionRepository.findById(event.transactionId()).orElse(null);
        }

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
                .build();

        Notification savedNotification = notificationRepository.save(notification);
        dispatchExternalNotification(savedNotification, user);
    }

    @Async
    public void dispatchExternalNotification(Notification notification, User user) {
        try {
            switch (notification.getNotificationType()) {
                case SMS -> {
                    if (user.getPhoneNumber() == null || user.getPhoneNumber().isBlank()) {
                        throw new IllegalArgumentException("User phone number is missing for SMS notification");
                    }
                    sendAfricasTalkingSms(user.getPhoneNumber(), notification.getMessage());
                }
                case EMAIL -> sendSendGridEmail(user.getEmail(), notification.getTitle(), notification.getMessage());
                case IN_APP -> log.info("In-app notification saved.");
            }
        } catch (Exception e) {
            log.error("Failed to dispatch notification ID: {}", notification.getId(), e);
            recordDispatchFailure(notification.getId(), e.getMessage());
        }
    }

    @Transactional
    public void recordDispatchFailure(UUID notificationId, String errorMessage) {
        try {
            notificationRepository.findById(notificationId).ifPresent(n -> {
                int currentRetries = n.getRetryCount() != null ? n.getRetryCount() : 0;
                n.setRetryCount(currentRetries + 1);
                n.setErrorMessage(errorMessage);
                notificationRepository.save(n);
            });
        } catch (Exception ex) {
            log.error("Failed to update retry count and error message for notification ID: {}", notificationId, ex);
        }
    }

    /**
     * Executes the Africa's Talking SMS API call.
     */
    private void sendAfricasTalkingSms(String phone, String message) {
        log.info("Initiating Africa's Talking SMS to {}", phone);

        try {
            String formattedPhone = normalizeToE164(phone);

            SmsService sms = AfricasTalking.getService(AfricasTalking.SERVICE_SMS);

            // Send the message. The SDK returns a list of Recipient objects with delivery statuses.
            List<Recipient> responses = sms.send(message, new String[]{formattedPhone}, true);

            for (Recipient recipient : responses) {
                log.info("SMS Status for {}: {}", recipient.number, recipient.status);
                if (recipient.status.equalsIgnoreCase("Failed") || recipient.status.equalsIgnoreCase("Rejected")) {
                    log.error("Africa's Talking rejected the SMS. Cost: {}, MessageId: {}", recipient.cost, recipient.messageId);
                }
            }
        } catch (Exception e) {
            log.error("Critical error while sending Africa's Talking SMS to {}. Reason: {}", phone, e.getMessage(), e);
            throw new RuntimeException("Africa's Talking API failure", e);
        }
    }

    private String normalizeToE164(String phone) {
        if (phone == null) {
            throw new IllegalArgumentException("Phone number cannot be null");
        }
        String cleaned = phone.trim().replaceAll("[\\s\\-\\(\\)]", "");
        if (cleaned.startsWith("+")) {
            return cleaned;
        }
        return "+" + cleaned;
    }

    private void sendSendGridEmail(String email, String subject, String body) {
        log.info("Mocking SendGrid Email to {}. Subject: {}", email, subject);
        // TODO: Implement SendGrid SDK logic here
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
