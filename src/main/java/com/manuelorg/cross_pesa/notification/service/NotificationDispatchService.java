package com.manuelorg.cross_pesa.notification.service;

import com.africastalking.AfricasTalking;
import com.africastalking.SmsService;
import com.africastalking.sms.Recipient;
import com.manuelorg.cross_pesa.auth.entity.User;
import com.manuelorg.cross_pesa.notification.entity.Notification;
import com.manuelorg.cross_pesa.notification.repository.NotificationRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Owns all external notification delivery (SMS/email).
 *
 * Lives in its own Spring bean so {@code @Async} and {@code @Transactional}
 * proxies are always honoured — the previous design called these methods
 * directly from NotificationService, silently bypassing both proxies.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationDispatchService {

    private final NotificationRepository notificationRepository;

    @Value("${africastalking.username}")
    private String atUsername;

    @Value("${africastalking.api-key}")
    private String atApiKey;

    @PostConstruct
    public void initAfricasTalking() {
        AfricasTalking.initialize(atUsername, atApiKey);
        log.info("Africa's Talking initialized for environment: {}", atUsername);
    }

    /**
     * Asynchronous external dispatch. Runs outside the caller's transaction via
     * the @Async proxy so slow provider HTTP calls never hold a DB transaction open.
     */
    @Async
    @Transactional
    public void dispatchExternal(UUID notificationId) {
        try {
            Notification notification = notificationRepository.findById(notificationId)
                    .orElseThrow(() -> new IllegalArgumentException("Notification not found: " + notificationId));
            User user = notification.getUser();
            user.getId(); // force lazy init inside the transaction

            doDispatch(notification, user);
        } catch (Exception e) {
            log.atError()
                    .addKeyValue("event", "notification.dispatch.failed")
                    .addKeyValue("notificationId", notificationId)
                    .setCause(e)
                    .log("Failed to dispatch notification");
            recordDispatchFailure(notificationId, e.getMessage());
        }
    }

    /**
     * Synchronous dispatch entrypoint used by the {@code NotificationPoller}.
     * Exceptions propagate so the poller can apply per-item error handling.
     */
    @Transactional
    public void dispatch(UUID notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found: " + notificationId));
        User user = notification.getUser();
        user.getId(); // force lazy initialization while the transaction is still open
        doDispatch(notification, user);
    }

    private void doDispatch(Notification notification, User user) {
        switch (notification.getNotificationType()) {
            case SMS -> {
                if (user.getPhoneNumber() == null || user.getPhoneNumber().isBlank()) {
                    throw new IllegalArgumentException("User phone number is missing for SMS notification");
                }
                sendAfricasTalkingSms(notification.getId(), user.getPhoneNumber(), notification.getMessage());
            }
            case EMAIL -> sendSendGridEmail(notification.getId(), user.getEmail(), notification.getTitle(), notification.getMessage());
            case IN_APP -> log.info("In-app notification saved.");
        }
        markDispatched(notification.getId());
    }

    @Transactional
    public void markDispatched(UUID notificationId) {
        notificationRepository.findById(notificationId).ifPresent(n -> n.setDispatchedAt(OffsetDateTime.now()));
    }

    @Transactional
    public void recordDispatchFailure(UUID notificationId, String errorMessage) {
        try {
            notificationRepository.findById(notificationId).ifPresent(n -> {
                int currentRetries = n.getRetryCount() != null ? n.getRetryCount() : 0;
                n.setRetryCount(currentRetries + 1);
                n.setErrorMessage(errorMessage);
                // no save() needed — managed entity inside a transaction
            });
        } catch (Exception ex) {
            log.atError()
                    .addKeyValue("event", "notification.retry.update.failed")
                    .addKeyValue("notificationId", notificationId)
                    .setCause(ex)
                    .log("Failed to update retry count and error message");
        }
    }

    private void sendAfricasTalkingSms(UUID notificationId, String phone, String message) {
        log.atInfo()
                .addKeyValue("event", "notification.sms.initiated")
                .addKeyValue("notificationId", notificationId)
                .addKeyValue("provider", "africastalking")
                .log("Initiating SMS dispatch");

        try {
            String formattedPhone = normalizeToE164(phone);

            SmsService sms = AfricasTalking.getService(AfricasTalking.SERVICE_SMS);

            List<Recipient> responses = sms.send(message, new String[]{formattedPhone}, true);

            for (Recipient recipient : responses) {
                log.atInfo()
                        .addKeyValue("event", "notification.sms.status")
                        .addKeyValue("notificationId", notificationId)
                        .addKeyValue("status", recipient.status)
                        .addKeyValue("messageId", recipient.messageId)
                        .log("SMS provider status");
                if (recipient.status.equalsIgnoreCase("Failed") || recipient.status.equalsIgnoreCase("Rejected")) {
                    log.atError()
                            .addKeyValue("event", "notification.sms.rejected")
                            .addKeyValue("notificationId", notificationId)
                            .addKeyValue("cost", recipient.cost)
                            .addKeyValue("messageId", recipient.messageId)
                            .log("Africa's Talking rejected the SMS");
                }
            }
        } catch (Exception e) {
            log.atError()
                    .addKeyValue("event", "notification.sms.error")
                    .addKeyValue("notificationId", notificationId)
                    .setCause(e)
                    .log("Critical error while sending Africa's Talking SMS");
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

    private void sendSendGridEmail(UUID notificationId, String email, String subject, String body) {
        log.atInfo()
                .addKeyValue("event", "notification.email.initiated")
                .addKeyValue("notificationId", notificationId)
                .addKeyValue("provider", "sendgrid")
                .log("Mocking SendGrid email dispatch");
        // TODO: Implement SendGrid SDK logic here
    }
}
