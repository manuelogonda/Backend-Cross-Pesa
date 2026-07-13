package com.manuelorg.cross_pesa.notification.repository;

import com.manuelorg.cross_pesa.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    // For fetching the user's in-app notification inbox, ordered by newest first
    List<Notification> findAllByUserIdOrderByCreatedAtDesc(UUID userId);

    // To prevent duplicate alerts for the same event
    Optional<Notification> findByIdempotencyKey(UUID idempotencyKey);
}
