package com.manuelorg.cross_pesa.notification.dto;

import com.manuelorg.cross_pesa.notification.enums.NotificationStatus;
import com.manuelorg.cross_pesa.notification.enums.NotificationType;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * DTO for returning notifications to the user's dashboard (In-App inbox)
 */
public record NotificationResponse(
        UUID id,
        String title,
        String message,
        NotificationType notificationType,
        NotificationStatus status,
        Map<String, Object> metadata,
        OffsetDateTime createdAt
) {}
