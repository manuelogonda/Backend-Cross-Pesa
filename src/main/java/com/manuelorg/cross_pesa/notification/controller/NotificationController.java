package com.manuelorg.cross_pesa.notification.controller;

import com.manuelorg.cross_pesa.auth.entity.User;
import com.manuelorg.cross_pesa.notification.dto.NotificationResponse;
import com.manuelorg.cross_pesa.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<List<NotificationResponse>> getUserNotifications(
            @AuthenticationPrincipal User currentUser) {

        List<NotificationResponse> notifications =
                notificationService.getUserDashboardNotifications(currentUser.getId());
        return ResponseEntity.ok(notifications);
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<Void> markNotificationAsRead(
            @PathVariable UUID id,
            @AuthenticationPrincipal User currentUser) {

        notificationService.markAsRead(id, currentUser.getId());
        return ResponseEntity.ok().build();
    }
}
