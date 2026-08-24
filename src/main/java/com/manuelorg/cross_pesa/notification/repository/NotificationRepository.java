package com.manuelorg.cross_pesa.notification.repository;

import com.manuelorg.cross_pesa.notification.entity.Notification;
import com.manuelorg.cross_pesa.notification.enums.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    // For fetching the user's in-app notification inbox, ordered by newest first
    Page<Notification> findAllByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    // To prevent duplicate alerts for the same event
    Optional<Notification> findByIdempotencyKey(UUID idempotencyKey);

    // Find notification by ID and User ID for scoped ownership checks
    Optional<Notification> findByIdAndUserId(UUID id, UUID userId);

    // Poller: fetch a bounded batch of notifications still awaiting external
    // dispatch, excluding IN_APP (delivered at creation) and exhausted retries.
    // Delivery state is dispatched_at — independent of the user's read status.
    List<Notification> findByDispatchedAtIsNullAndNotificationTypeNotAndRetryCountLessThan(
            NotificationType type, int maxRetryCount, Pageable pageable);
}
