package com.manuelorg.cross_pesa.features.notifications;

import com.manuelorg.cross_pesa.features.notifications.enums.NotificationStatus;
import com.manuelorg.cross_pesa.features.notifications.enums.NotificationType;
import com.manuelorg.cross_pesa.features.user.User;
import jakarta.persistence.*;
import lombok.Builder;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
public class Notification {
    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_notifications_user"))
    private User user;

    @Column(name = "title", nullable = false, length = 150)
    private String title;

    // Use columnDefinition = "TEXT" to cleanly map to PostgreSQL's unbounded TEXT type
    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", length = 20)
    @Builder.Default
    private NotificationType notificationType = NotificationType.SMS;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private NotificationStatus status = NotificationStatus.UNREAD;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
