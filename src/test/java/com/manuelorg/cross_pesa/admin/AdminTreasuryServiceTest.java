package com.manuelorg.cross_pesa.admin;

import com.manuelorg.cross_pesa.admin.dto.TreasuryRebalanceRequest;
import com.manuelorg.cross_pesa.admin.service.AdminTreasuryService;
import com.manuelorg.cross_pesa.auth.entity.Role;
import com.manuelorg.cross_pesa.auth.entity.User;
import com.manuelorg.cross_pesa.systemEngine.SystemWalletEngine;
import com.manuelorg.cross_pesa.transaction.entity.Transaction;
import com.manuelorg.cross_pesa.transaction.repository.TransactionRepository;
import com.manuelorg.cross_pesa.wallet.dto.WalletResponse;
import com.manuelorg.cross_pesa.wallet.entity.Wallet;
import com.manuelorg.cross_pesa.wallet.enums.Currency;
import com.manuelorg.cross_pesa.wallet.enums.WalletStatus;
import com.manuelorg.cross_pesa.wallet.enums.WalletType;
import com.manuelorg.cross_pesa.wallet.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminTreasuryServiceTest {

    @Mock
    private SystemWalletEngine systemWalletEngine;

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private AdminTreasuryService adminTreasuryService;

    private User adminUser;
    private Wallet sourceLiquidity;
    private Wallet targetLiquidity;

    @BeforeEach
    void setUp() {
        adminUser = User.builder()
                .id(UUID.randomUUID())
                .firstName("Admin")
                .lastName("Super")
                .email("admin@crosspesa.com")
                .role(Role.ADMIN)
                .build();

        sourceLiquidity = Wallet.builder()
                .id(UUID.randomUUID())
                .currency(Currency.KES)
                .walletType(WalletType.SYSTEM_LIQUIDITY)
                .balance(new BigDecimal("500000.0000"))
                .lockedBalance(BigDecimal.ZERO)
                .status(WalletStatus.ACTIVE)
                .build();

        targetLiquidity = Wallet.builder()
                .id(UUID.randomUUID())
                .currency(Currency.USD)
                .walletType(WalletType.SYSTEM_LIQUIDITY)
                .balance(new BigDecimal("10000.0000"))
                .lockedBalance(BigDecimal.ZERO)
                .status(WalletStatus.ACTIVE)
                .build();
    }

    @Test
    void getSystemWallets_WhenTypeIsUserRetail_ThrowsException() {
        Pageable pageable = PageRequest.of(0, 10);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                adminTreasuryService.getSystemWallets(WalletType.USER_RETAIL, pageable)
        );
        assertEquals("Treasury cannot query retail wallets.", ex.getMessage());
    }

    @Test
    void getSystemWallets_WhenTypeIsSystemLiquidity_ReturnsWallets() {
        Pageable pageable = PageRequest.of(0, 10);
        when(walletRepository.findByWalletType(WalletType.SYSTEM_LIQUIDITY, pageable))
                .thenReturn(new PageImpl<>(List.of(sourceLiquidity)));

        Page<WalletResponse> result = adminTreasuryService.getSystemWallets(WalletType.SYSTEM_LIQUIDITY, pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(Currency.KES, result.getContent().getFirst().currency());
    }

    @Test
    void executeRebalance_CreatesAuditTransactionAndCallsSystemEngine() {
        TreasuryRebalanceRequest request = new TreasuryRebalanceRequest(
                Currency.KES,
                new BigDecimal("100000.0000"),
                Currency.USD,
                new BigDecimal("750.0000"),
                "Periodic liquidity rebalance"
        );

        when(systemWalletEngine.getSystemWallet(Currency.KES, WalletType.SYSTEM_LIQUIDITY)).thenReturn(sourceLiquidity);
        when(systemWalletEngine.getSystemWallet(Currency.USD, WalletType.SYSTEM_LIQUIDITY)).thenReturn(targetLiquidity);

        adminTreasuryService.executeRebalance(adminUser, request);

        ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(txCaptor.capture());

        Transaction savedTx = txCaptor.getValue();
        assertEquals(adminUser, savedTx.getSender());
        assertEquals(Currency.KES, savedTx.getSourceCurrency());
        assertEquals(Currency.USD, savedTx.getDestinationCurrency());
        assertEquals(new BigDecimal("100000.0000"), savedTx.getGrossAmount());
        assertEquals(new BigDecimal("750.0000"), savedTx.getDestinationAmount());
        assertNotNull(savedTx.getGatewayReference());
        assertTrue(savedTx.getGatewayReference().startsWith("TREASURY-REBALANCE"));

        verify(systemWalletEngine).executeTreasuryRebalance(
                eq(savedTx),
                eq(Currency.KES),
                eq(new BigDecimal("100000.0000")),
                eq(Currency.USD),
                eq(new BigDecimal("750.0000")),
                eq("Periodic liquidity rebalance")
        );
    }
}
