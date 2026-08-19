package com.manuelorg.cross_pesa.admin;

import com.manuelorg.cross_pesa.admin.dto.AdminTransactionResponse;
import com.manuelorg.cross_pesa.admin.dto.AdminUserDto;
import com.manuelorg.cross_pesa.admin.dto.DashboardMetricsResponse;
import com.manuelorg.cross_pesa.admin.service.AdminDashboardService;
import com.manuelorg.cross_pesa.auth.entity.KycStatus;
import com.manuelorg.cross_pesa.auth.entity.User;
import com.manuelorg.cross_pesa.auth.entity.UserStatus;
import com.manuelorg.cross_pesa.auth.repository.UserRepository;
import com.manuelorg.cross_pesa.transaction.entity.Transaction;
import com.manuelorg.cross_pesa.transaction.enums.TransactionStatus;
import com.manuelorg.cross_pesa.transaction.repository.TransactionRepository;
import com.manuelorg.cross_pesa.wallet.enums.Currency;
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

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminDashboardServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AdminDashboardService adminDashboardService;

    private User testUser;
    private Transaction testTransaction;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(UUID.randomUUID())
                .firstName("John")
                .lastName("Doe")
                .email("john.doe@example.com")
                .phoneNumber("+254700000000")
                .idType("NATIONAL_ID")
                .idNumber("12345678")
                .status(UserStatus.ACTIVE)
                .kycStatus(KycStatus.APPROVED)
                .kycLevel(2)
                .createdAt(OffsetDateTime.now())
                .build();

        testTransaction = Transaction.builder()
                .id(UUID.randomUUID())
                .sender(testUser)
                .sourceCurrency(Currency.KES)
                .destinationCurrency(Currency.USD)
                .grossAmount(new BigDecimal("1000.0000"))
                .netAmount(new BigDecimal("990.0000"))
                .destinationAmount(new BigDecimal("7.5000"))
                .fxRateApplied(new BigDecimal("0.0075"))
                .usdNormalizationRate(new BigDecimal("0.0075"))
                .markupFee(new BigDecimal("6.0000"))
                .routingFee(new BigDecimal("4.0000"))
                .totalFee(new BigDecimal("10.0000"))
                .status(TransactionStatus.COMPLETED)
                .gatewayReference("GW-12345")
                .createdAt(OffsetDateTime.now())
                .build();
    }

    @Test
    void getMetrics_CalculatesAndReturnsAccurateMetrics() {
        when(transactionRepository.countByCreatedAtAfter(any(OffsetDateTime.class))).thenReturn(15L);
        when(transactionRepository.countByStatusIn(any())).thenReturn(3L);
        when(transactionRepository.countByStatus(TransactionStatus.FLAGGED)).thenReturn(1L);
        when(transactionRepository.countByStatusAndCreatedAtAfter(eq(TransactionStatus.COMPLETED), any(OffsetDateTime.class))).thenReturn(11L);
        when(transactionRepository.sumTotalFeeByCreatedAtAfter(any(OffsetDateTime.class))).thenReturn(new BigDecimal("150.0000"));
        when(transactionRepository.sumNetMarkupRevenueSince(any(OffsetDateTime.class), eq(TransactionStatus.COMPLETED))).thenReturn(new BigDecimal("90.0000"));

        DashboardMetricsResponse metrics = adminDashboardService.getMetrics();

        assertNotNull(metrics);
        assertEquals(15L, metrics.totalTransactionsToday());
        assertEquals(3L, metrics.pendingTransactions());
        assertEquals(1L, metrics.flaggedTransactions());
        assertEquals(11L, metrics.completedTransactionsToday());
        assertEquals(new BigDecimal("150.0000"), metrics.totalRevenueToday());
        assertEquals(new BigDecimal("90.0000"), metrics.netMarkupRevenueToday());
    }

    @Test
    void getMetrics_HandlesNullRevenueSafely() {
        when(transactionRepository.countByCreatedAtAfter(any(OffsetDateTime.class))).thenReturn(0L);
        when(transactionRepository.countByStatusIn(any())).thenReturn(0L);
        when(transactionRepository.countByStatus(TransactionStatus.FLAGGED)).thenReturn(0L);
        when(transactionRepository.countByStatusAndCreatedAtAfter(eq(TransactionStatus.COMPLETED), any(OffsetDateTime.class))).thenReturn(0L);
        when(transactionRepository.sumTotalFeeByCreatedAtAfter(any(OffsetDateTime.class))).thenReturn(null);
        when(transactionRepository.sumNetMarkupRevenueSince(any(OffsetDateTime.class), eq(TransactionStatus.COMPLETED))).thenReturn(null);

        DashboardMetricsResponse metrics = adminDashboardService.getMetrics();

        assertNotNull(metrics);
        assertEquals(BigDecimal.ZERO, metrics.totalRevenueToday());
        assertEquals(BigDecimal.ZERO, metrics.netMarkupRevenueToday());
    }

    @Test
    void getTransactions_WithAllFilter_ReturnsAllTransactions() {
        Pageable pageable = PageRequest.of(0, 10);
        when(transactionRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(testTransaction)));

        Page<AdminTransactionResponse> result = adminDashboardService.getTransactions("ALL", pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(testTransaction.getId(), result.getContent().getFirst().transactionId());
    }

    @Test
    void getTransactions_WithSpecificStatus_ReturnsFilteredTransactions() {
        Pageable pageable = PageRequest.of(0, 10);
        when(transactionRepository.findByStatus(TransactionStatus.COMPLETED, pageable))
                .thenReturn(new PageImpl<>(List.of(testTransaction)));

        Page<AdminTransactionResponse> result = adminDashboardService.getTransactions("COMPLETED", pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(testTransaction.getId(), result.getContent().getFirst().transactionId());
    }

    @Test
    void getUsers_UsesFactoryMethodAndReturnsPage() {
        Pageable pageable = PageRequest.of(0, 10);
        when(userRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(testUser)));

        Page<AdminUserDto.AdminUserResponse> result = adminDashboardService.getUsers(pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        AdminUserDto.AdminUserResponse userResponse = result.getContent().getFirst();
        assertEquals(testUser.getId(), userResponse.id());
        assertEquals(testUser.getFirstName(), userResponse.firstName());
        assertEquals(testUser.getEmail(), userResponse.email());
        assertEquals(testUser.getKycLevel(), userResponse.kycLevel());
    }
}
