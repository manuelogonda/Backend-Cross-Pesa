package com.manuelorg.cross_pesa.admin;

import com.manuelorg.cross_pesa.admin.dto.AdminUserDto;
import com.manuelorg.cross_pesa.admin.service.AdminUserOpsService;
import com.manuelorg.cross_pesa.auth.entity.KycStatus;
import com.manuelorg.cross_pesa.auth.entity.Role;
import com.manuelorg.cross_pesa.auth.entity.User;
import com.manuelorg.cross_pesa.auth.entity.UserStatus;
import com.manuelorg.cross_pesa.auth.repository.UserRepository;
import com.manuelorg.cross_pesa.kycSubmission.entity.KycSubmission;
import com.manuelorg.cross_pesa.kycSubmission.repository.KycSubmissionRepository;
import com.manuelorg.cross_pesa.ledger.dto.LedgerEntryResponse;
import com.manuelorg.cross_pesa.ledger.entity.LedgerEntry;
import com.manuelorg.cross_pesa.ledger.enums.EntryClass;
import com.manuelorg.cross_pesa.ledger.repository.LedgerEntryRepository;
import com.manuelorg.cross_pesa.wallet.dto.WalletResponse;
import com.manuelorg.cross_pesa.wallet.entity.Wallet;
import com.manuelorg.cross_pesa.wallet.enums.Currency;
import com.manuelorg.cross_pesa.wallet.enums.WalletStatus;
import com.manuelorg.cross_pesa.wallet.enums.WalletType;
import com.manuelorg.cross_pesa.wallet.repository.WalletRepository;
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
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminUserOpsServiceTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private LedgerEntryRepository ledgerEntryRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private KycSubmissionRepository kycSubmissionRepository;

    @InjectMocks
    private AdminUserOpsService adminUserOpsService;

    private User targetUser;
    private User adminUser;
    private Wallet retailWallet;

    @BeforeEach
    void setUp() {
        UUID userId = UUID.randomUUID();
        targetUser = User.builder()
                .id(userId)
                .firstName("Alice")
                .lastName("Smith")
                .email("alice@example.com")
                .phoneNumber("+254711111111")
                .status(UserStatus.ACTIVE)
                .kycStatus(KycStatus.PENDING)
                .kycLevel(1)
                .build();

        adminUser = User.builder()
                .id(UUID.randomUUID())
                .firstName("Super")
                .lastName("Admin")
                .email("admin@crosspesa.com")
                .role(Role.ADMIN)
                .build();

        retailWallet = Wallet.builder()
                .id(UUID.randomUUID())
                .user(targetUser)
                .currency(Currency.KES)
                .walletType(WalletType.USER_RETAIL)
                .balance(new BigDecimal("5000.0000"))
                .lockedBalance(BigDecimal.ZERO)
                .status(WalletStatus.ACTIVE)
                .build();
    }

    @Test
    void getUserRetailWallet_ReturnsWallet() {
        when(walletRepository.findByUserIdAndWalletType(targetUser.getId(), WalletType.USER_RETAIL))
                .thenReturn(Optional.of(retailWallet));

        WalletResponse response = adminUserOpsService.getUserRetailWallet(targetUser.getId());

        assertNotNull(response);
        assertEquals(retailWallet.getId(), response.id());
        assertEquals(Currency.KES, response.currency());
    }

    @Test
    void getUserRetailWallet_NotFound_ThrowsException() {
        UUID randomId = UUID.randomUUID();
        when(walletRepository.findByUserIdAndWalletType(randomId, WalletType.USER_RETAIL))
                .thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> adminUserOpsService.getUserRetailWallet(randomId));
    }

    @Test
    void getUserLedger_ReturnsPageOfLedgerEntries() {
        Pageable pageable = PageRequest.of(0, 10);
        when(walletRepository.findByUserIdAndWalletType(targetUser.getId(), WalletType.USER_RETAIL))
                .thenReturn(Optional.of(retailWallet));

        LedgerEntry entry = LedgerEntry.builder()
                .id(UUID.randomUUID())
                .wallet(retailWallet)
                .currency(Currency.KES)
                .debit(BigDecimal.ZERO)
                .credit(new BigDecimal("100.0000"))
                .entryClass(EntryClass.DEPOSIT)
                .description("Test deposit")
                .balanceAfter(new BigDecimal("5100.0000"))
                .createdAt(OffsetDateTime.now())
                .build();

        when(ledgerEntryRepository.findByWalletIdOrderByCreatedAtDesc(retailWallet.getId(), pageable))
                .thenReturn(new PageImpl<>(List.of(entry)));

        Page<LedgerEntryResponse> result = adminUserOpsService.getUserLedger(targetUser.getId(), pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(new BigDecimal("100.0000"), result.getContent().getFirst().credit());
    }

    @Test
    void updateWalletStatus_UpdatesAndReturnsWallet() {
        when(walletRepository.findByUserIdAndWalletType(targetUser.getId(), WalletType.USER_RETAIL))
                .thenReturn(Optional.of(retailWallet));
        when(walletRepository.save(any(Wallet.class))).thenAnswer(invocation -> invocation.getArgument(0));

        WalletResponse response = adminUserOpsService.updateWalletStatus(
                targetUser.getId(),
                WalletStatus.SUSPENDED,
                "Suspicious activity detected",
                adminUser.getEmail()
        );

        assertNotNull(response);
        assertEquals(WalletStatus.SUSPENDED.name(), response.status());
        verify(walletRepository).save(retailWallet);
    }

    @Test
    void updateUserKyc_UpdatesUserAndKycSubmission() {
        when(userRepository.findById(targetUser.getId())).thenReturn(Optional.of(targetUser));

        KycSubmission submission = KycSubmission.builder()
                .id(UUID.randomUUID())
                .user(targetUser)
                .smileJobId("SMILE-999")
                .documentType("PASSPORT")
                .documentCountry("KE")
                .status("PENDING")
                .build();

        when(kycSubmissionRepository.findByUserIdOrderByCreatedAtDesc(targetUser.getId()))
                .thenReturn(List.of(submission));

        AdminUserDto.UpdateKycRequest request = new AdminUserDto.UpdateKycRequest(
                KycStatus.APPROVED,
                2,
                "Verified all documents successfully"
        );

        adminUserOpsService.updateUserKyc(targetUser.getId(), request, adminUser);

        assertEquals(KycStatus.APPROVED, targetUser.getKycStatus());
        assertEquals(2, targetUser.getKycLevel());
        verify(userRepository).save(targetUser);

        assertEquals("APPROVED", submission.getStatus());
        assertEquals("Verified all documents successfully", submission.getRejectionReason());
        assertEquals(adminUser, submission.getReviewedBy());
        assertNotNull(submission.getReviewedAt());
        verify(kycSubmissionRepository).save(submission);
    }
}
