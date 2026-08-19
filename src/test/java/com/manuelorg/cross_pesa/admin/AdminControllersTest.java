package com.manuelorg.cross_pesa.admin;

import com.manuelorg.cross_pesa.admin.controller.AdminController;
import com.manuelorg.cross_pesa.admin.controller.AdminTreasuryController;
import com.manuelorg.cross_pesa.admin.controller.AdminUserOpsController;
import com.manuelorg.cross_pesa.admin.dto.*;
import com.manuelorg.cross_pesa.admin.service.AdminDashboardService;
import com.manuelorg.cross_pesa.admin.service.AdminTreasuryService;
import com.manuelorg.cross_pesa.admin.service.AdminUserOpsService;
import com.manuelorg.cross_pesa.auth.entity.KycStatus;
import com.manuelorg.cross_pesa.auth.entity.Role;
import com.manuelorg.cross_pesa.auth.entity.User;
import com.manuelorg.cross_pesa.auth.entity.UserStatus;
import com.manuelorg.cross_pesa.wallet.dto.WalletResponse;
import com.manuelorg.cross_pesa.wallet.enums.Currency;
import com.manuelorg.cross_pesa.wallet.enums.WalletStatus;
import com.manuelorg.cross_pesa.wallet.enums.WalletType;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
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

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminControllersTest {

    @Mock
    private AdminDashboardService adminDashboardService;

    @Mock
    private AdminTreasuryService adminTreasuryService;

    @Mock
    private AdminUserOpsService adminUserOpsService;

    @InjectMocks
    private AdminController adminController;

    @InjectMocks
    private AdminTreasuryController adminTreasuryController;

    @InjectMocks
    private AdminUserOpsController adminUserOpsController;

    private Validator validator;
    private User adminUser;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();

        adminUser = User.builder()
                .id(UUID.randomUUID())
                .firstName("Admin")
                .lastName("User")
                .email("admin@crosspesa.com")
                .role(Role.ADMIN)
                .build();
    }

    @Test
    void adminUserDto_Validation_BlankReasonFails() {
        AdminUserDto.UpdateStatusRequest invalidRequest = new AdminUserDto.UpdateStatusRequest(
                WalletStatus.SUSPENDED,
                "   "
        );
        var violations = validator.validate(invalidRequest);
        assertFalse(violations.isEmpty());
    }

    @Test
    void adminUserDto_Validation_ValidRequestPasses() {
        AdminUserDto.UpdateStatusRequest validRequest = new AdminUserDto.UpdateStatusRequest(
                WalletStatus.SUSPENDED,
                "Account suspended due to fraud risk"
        );
        var violations = validator.validate(validRequest);
        assertTrue(violations.isEmpty());
    }

    @Test
    void updateKycRequest_Validation_BlankNotesFails() {
        AdminUserDto.UpdateKycRequest invalid = new AdminUserDto.UpdateKycRequest(
                KycStatus.APPROVED,
                2,
                ""
        );
        var violations = validator.validate(invalid);
        assertFalse(violations.isEmpty());
    }

    @Test
    void treasuryRebalanceRequest_Validation_BlankNotesFails() {
        TreasuryRebalanceRequest invalid = new TreasuryRebalanceRequest(
                Currency.KES,
                new BigDecimal("100"),
                Currency.USD,
                new BigDecimal("1"),
                " "
        );
        var violations = validator.validate(invalid);
        assertFalse(violations.isEmpty());
    }

    @Test
    void adminController_GetMetrics_ReturnsResponse() {
        DashboardMetricsResponse metrics = new DashboardMetricsResponse(
                10L, 2L, 0L, new BigDecimal("100.00"), new BigDecimal("50.00"), 8L
        );
        when(adminDashboardService.getMetrics()).thenReturn(metrics);

        ResponseEntity<DashboardMetricsResponse> response = adminController.getMetrics();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(metrics, response.getBody());
    }

    @Test
    void adminTreasuryController_RebalancePools_ReturnsTypedResponse() {
        TreasuryRebalanceRequest request = new TreasuryRebalanceRequest(
                Currency.KES,
                new BigDecimal("50000.00"),
                Currency.USD,
                new BigDecimal("380.00"),
                "Rebalancing liquidity"
        );

        ResponseEntity<AdminMessageResponse> response = adminTreasuryController.rebalancePools(adminUser, request);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Treasury rebalance executed and logged successfully.", response.getBody().message());
        verify(adminTreasuryService).executeRebalance(adminUser, request);
    }

    @Test
    void adminUserOpsController_ChangeWalletStatus_ReturnsTypedResponse() {
        UUID userId = UUID.randomUUID();
        AdminUserDto.UpdateStatusRequest request = new AdminUserDto.UpdateStatusRequest(
                WalletStatus.SUSPENDED,
                "Suspicious transactions"
        );

        WalletResponse mockWallet = new WalletResponse(
                UUID.randomUUID(),
                Currency.KES,
                WalletType.USER_RETAIL,
                new BigDecimal("1000.0000"),
                BigDecimal.ZERO,
                new BigDecimal("1000.0000"),
                WalletStatus.SUSPENDED.name()
        );

        when(adminUserOpsService.updateWalletStatus(userId, WalletStatus.SUSPENDED, "Suspicious transactions", "admin@crosspesa.com"))
                .thenReturn(mockWallet);

        ResponseEntity<AdminUserDto.AdminWalletStatusResponse> response = adminUserOpsController.changeWalletStatus(
                userId,
                request,
                adminUser
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Wallet status updated successfully", response.getBody().message());
        assertEquals(WalletStatus.SUSPENDED.name(), response.getBody().wallet().status());
    }

    @Test
    void adminUserOpsController_UpdateKycStatus_Returns204NoContent() {
        UUID userId = UUID.randomUUID();
        AdminUserDto.UpdateKycRequest request = new AdminUserDto.UpdateKycRequest(
                KycStatus.APPROVED,
                2,
                "Admin approval"
        );

        ResponseEntity<Void> response = adminUserOpsController.updateKycStatus(userId, request, adminUser);
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(adminUserOpsService).updateUserKyc(userId, request, adminUser);
    }
}
