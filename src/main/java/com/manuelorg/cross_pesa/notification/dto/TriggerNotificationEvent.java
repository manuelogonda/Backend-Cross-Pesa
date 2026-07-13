package com.manuelorg.cross_pesa.notification.dto;

import com.manuelorg.cross_pesa.notification.enums.NotificationType;

import java.util.Map;
import java.util.UUID;

/**
 * Internal DTO used to pass notification data between your TransactionService
 * and your NotificationService via Spring Events (@Async)
 */
public record TriggerNotificationEvent(
        UUID userId,
        UUID transactionId,
        String title,
        String message,
        NotificationType type,
        Map<String, Object> metadata
) {}
