package com.manuelorg.cross_pesa.notification;

import com.manuelorg.cross_pesa.auth.entity.Role;
import com.manuelorg.cross_pesa.auth.entity.User;
import com.manuelorg.cross_pesa.notification.controller.NotificationController;
import com.manuelorg.cross_pesa.notification.dto.NotificationResponse;
import com.manuelorg.cross_pesa.notification.enums.NotificationStatus;
import com.manuelorg.cross_pesa.notification.enums.NotificationType;
import com.manuelorg.cross_pesa.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private NotificationController notificationController;

    private User currentUser;

    @BeforeEach
    void setUp() {
        currentUser = User.builder()
                .id(UUID.randomUUID())
                .email("user@example.com")
                .role(Role.USER)
                .build();
    }

    @Test
    void getUserNotifications_ReturnsOkWithPage() {
        Pageable pageable = PageRequest.of(0, 10);
        NotificationResponse response = new NotificationResponse(
                UUID.randomUUID(),
                "Title",
                "Message",
                NotificationType.IN_APP,
                NotificationStatus.UNREAD,
                Map.of(),
                OffsetDateTime.now()
        );

        Page<NotificationResponse> page = new PageImpl<>(List.of(response));
        when(notificationService.getUserDashboardNotifications(currentUser.getId(), pageable)).thenReturn(page);

        ResponseEntity<Page<NotificationResponse>> result =
                notificationController.getUserNotifications(currentUser, pageable);

        assertNotNull(result);
        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals(1, result.getBody().getTotalElements());
        verify(notificationService).getUserDashboardNotifications(currentUser.getId(), pageable);
    }

    @Test
    void markNotificationAsRead_CallsServiceAndReturnsOk() {
        UUID notificationId = UUID.randomUUID();
        doNothing().when(notificationService).markAsRead(notificationId, currentUser.getId());

        ResponseEntity<Void> result = notificationController.markNotificationAsRead(notificationId, currentUser);

        assertNotNull(result);
        assertEquals(HttpStatus.OK, result.getStatusCode());
        verify(notificationService).markAsRead(notificationId, currentUser.getId());
    }
}
