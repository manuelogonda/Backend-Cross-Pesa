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
import jakarta.annotation.PostConstruct;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
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
        UUID idempotencyKey = UUID.nameUUIDFromBytes((event.transactionId().toString() + event.type().name()).getBytes());
        if (notificationRepository.findByIdempotencyKey(idempotencyKey).isPresent()) {
            log.warn("Notification already processed for transaction: {}", event.transactionId());
            return;
        }

        User user = userRepository.findById(event.userId())
                .orElseThrow(() -> new IllegalArgumentException("User not found for notification"));

        Notification notification = Notification.builder()
                .user(user)
                .title(event.title())
                .message(event.message())
                .notificationType(event.type())
                .metadata(event.metadata())
                .idempotencyKey(idempotencyKey)
                .status(NotificationStatus.UNREAD)
                .build();

        Notification savedNotification = notificationRepository.save(notification);
        dispatchExternalNotification(savedNotification, user);
    }

    @Async
    public void dispatchExternalNotification(Notification notification, User user) {
        try {
            switch (notification.getNotificationType()) {
                case SMS -> sendAfricasTalkingSms(user.getPhoneNumber(), notification.getMessage());
                case EMAIL -> sendSendGridEmail(user.getEmail(), notification.getTitle(), notification.getMessage());
                case IN_APP -> log.info("In-app notification saved.");
            }
        } catch (Exception e) {
            log.error("Failed to dispatch notification ID: {}", notification.getId(), e);
            // Future implementation: Update retry_count and error_message in DB here
        }
    }

    /**
     * Executes the Africa's Talking SMS API call.
     */
    private void sendAfricasTalkingSms(String phone, String message) {
        log.info("Initiating Africa's Talking SMS to {}", phone);

        try {
            // Africa's Talking STRICTLY requires standard international format (e.g., +2547...)
            String formattedPhone = phone.startsWith("+") ? phone : "+" + phone;

            SmsService sms = AfricasTalking.getService(AfricasTalking.SERVICE_SMS);

            // Send the message. The SDK returns a list of Recipient objects with delivery statuses.
            List<Recipient> responses = sms.send(message, new String[]{formattedPhone},true);

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
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found"));

        if (!notification.getUser().getId().equals(userId)) {
            throw new SecurityException("Unauthorized access to notification");
        }

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
