package com.manuelorg.cross_pesa.beneficiaries;

import com.manuelorg.cross_pesa.auth.entity.User;
import com.manuelorg.cross_pesa.beneficiaries.dto.BeneficiaryRequest;
import com.manuelorg.cross_pesa.beneficiaries.dto.BeneficiaryResponse;
import com.manuelorg.cross_pesa.beneficiaries.entity.Beneficiary;
import com.manuelorg.cross_pesa.beneficiaries.entity.BeneficiaryType;
import com.manuelorg.cross_pesa.beneficiaries.entity.PayoutMethod;
import com.manuelorg.cross_pesa.beneficiaries.entity.PayoutProvider;
import com.manuelorg.cross_pesa.beneficiaries.repository.BeneficiaryRepository;
import com.manuelorg.cross_pesa.beneficiaries.service.BeneficiaryService;
import com.manuelorg.cross_pesa.wallet.enums.Currency;
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

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BeneficiaryServiceTest {

    @Mock
    private BeneficiaryRepository beneficiaryRepository;

    @Mock
    private com.manuelorg.cross_pesa.payment.flutterwave.FlutterwaveTransferService flutterwaveTransferService;

    @InjectMocks
    private BeneficiaryService beneficiaryService;

    private User currentUser;
    private Beneficiary beneficiary;
    private UUID beneficiaryId;

    @BeforeEach
    void setUp() {
        beneficiaryId = UUID.randomUUID();
        currentUser = User.builder()
                .id(UUID.randomUUID())
                .email("user@example.com")
                .firstName("John")
                .lastName("Doe")
                .build();

        beneficiary = Beneficiary.builder()
                .id(beneficiaryId)
                .user(currentUser)
                .firstName("Alice")
                .lastName("Smith")
                .beneficiaryType(BeneficiaryType.INDIVIDUAL)
                .email("alice@example.com")
                .phoneNumber("+254711223344")
                .countryCode("KE")
                .city("Nairobi")
                .payoutMethod(PayoutMethod.MOBILE_MONEY)
                .payoutProvider(PayoutProvider.MPESA)
                .accountNumber("254711223344")
                .accountCurrency(Currency.KES)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
    }

    @Test
    void getUserBeneficiaries_ReturnsPagedBeneficiaries() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Beneficiary> page = new PageImpl<>(List.of(beneficiary));
        when(beneficiaryRepository.findAllByUserId(currentUser.getId(), pageable)).thenReturn(page);

        Page<BeneficiaryResponse> result = beneficiaryService.getUserBeneficiaries(currentUser.getId(), pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("Alice", result.getContent().getFirst().firstName());
        assertEquals("M-PESA", result.getContent().getFirst().payoutProvider());
        verify(beneficiaryRepository).findAllByUserId(currentUser.getId(), pageable);
    }

    @Test
    void createBeneficiary_Success() {
        BeneficiaryRequest request = new BeneficiaryRequest(
                " Alice ",
                " Smith ",
                BeneficiaryType.INDIVIDUAL,
                " alice@example.com ",
                " +254711223344 ",
                "ke",
                " Nairobi ",
                PayoutMethod.MOBILE_MONEY,
                PayoutProvider.MPESA,
                " 254711223344 ",
                "MPESA",
                Currency.KES
        );

        when(beneficiaryRepository.existsByUserIdAndPayoutProviderAndAccountNumber(
                currentUser.getId(), PayoutProvider.MPESA, "254711223344"
        )).thenReturn(false);

        when(beneficiaryRepository.save(any(Beneficiary.class))).thenAnswer(invocation -> {
            Beneficiary b = invocation.getArgument(0);
            b.setId(beneficiaryId);
            return b;
        });

        BeneficiaryResponse response = beneficiaryService.createBeneficiary(currentUser, request);

        assertNotNull(response);
        assertEquals("Alice", response.firstName());
        assertEquals("Smith", response.lastName());
        assertEquals("KE", response.countryCode());
        assertEquals("254711223344", response.accountNumber());

        ArgumentCaptor<Beneficiary> captor = ArgumentCaptor.forClass(Beneficiary.class);
        verify(beneficiaryRepository).save(captor.capture());
        Beneficiary saved = captor.getValue();
        assertEquals("KE", saved.getCountryCode());
        assertEquals("Alice", saved.getFirstName());
        assertEquals("Smith", saved.getLastName());
        assertEquals("alice@example.com", saved.getEmail());
    }

    @Test
    void createBeneficiary_DuplicateRouting_ThrowsException() {
        BeneficiaryRequest request = new BeneficiaryRequest(
                "Alice",
                "Smith",
                BeneficiaryType.INDIVIDUAL,
                "alice@example.com",
                "+254711223344",
                "KE",
                "Nairobi",
                PayoutMethod.MOBILE_MONEY,
                PayoutProvider.MPESA,
                "254711223344",
                "MPESA",
                Currency.KES
        );

        when(beneficiaryRepository.existsByUserIdAndPayoutProviderAndAccountNumber(
                currentUser.getId(), PayoutProvider.MPESA, "254711223344"
        )).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> beneficiaryService.createBeneficiary(currentUser, request));

        assertTrue(ex.getMessage().contains("already saved a beneficiary with this exact account number and provider"));
        verify(beneficiaryRepository, never()).save(any());
    }

    @Test
    void deleteBeneficiary_Success() {
        when(beneficiaryRepository.findByIdAndUserId(beneficiaryId, currentUser.getId()))
                .thenReturn(Optional.of(beneficiary));

        beneficiaryService.deleteBeneficiary(currentUser, beneficiaryId);

        verify(beneficiaryRepository).delete(beneficiary);
    }

    @Test
    void deleteBeneficiary_NotFoundOrUnauthorized_ThrowsException() {
        when(beneficiaryRepository.findByIdAndUserId(beneficiaryId, currentUser.getId()))
                .thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> beneficiaryService.deleteBeneficiary(currentUser, beneficiaryId));

        assertTrue(ex.getMessage().contains("Beneficiary not found or unauthorized"));
        verify(beneficiaryRepository, never()).delete(any());
    }

    @Test
    void updateBeneficiary_Success() {
        BeneficiaryRequest request = new BeneficiaryRequest(
                " Alice Updated ",
                " Smith ",
                BeneficiaryType.INDIVIDUAL,
                " alice.new@example.com ",
                " +254711223344 ",
                "ug",
                " Kampala ",
                PayoutMethod.BANK_TRANSFER,
                PayoutProvider.EQUITY_BANK,
                " 987654321 ",
                "EQUITYKE",
                Currency.EUR
        );

        when(beneficiaryRepository.findByIdAndUserId(beneficiaryId, currentUser.getId()))
                .thenReturn(Optional.of(beneficiary));
        when(beneficiaryRepository.existsByUserIdAndPayoutProviderAndAccountNumber(
                currentUser.getId(), PayoutProvider.EQUITY_BANK, "987654321"
        )).thenReturn(false);
        when(beneficiaryRepository.save(any(Beneficiary.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BeneficiaryResponse response = beneficiaryService.updateBeneficiary(currentUser, beneficiaryId, request);

        assertNotNull(response);
        assertEquals("Alice Updated", response.firstName());
        assertEquals("UG", response.countryCode());
        assertEquals("EQUITY BANK", response.payoutProvider());
        assertEquals("987654321", response.accountNumber());
        assertEquals("EUR", response.accountCurrency());
    }

    @Test
    void updateBeneficiary_RoutingConflict_ThrowsException() {
        BeneficiaryRequest request = new BeneficiaryRequest(
                "Alice",
                "Smith",
                BeneficiaryType.INDIVIDUAL,
                "alice@example.com",
                "+254711223344",
                "KE",
                "Nairobi",
                PayoutMethod.BANK_TRANSFER,
                PayoutProvider.EQUITY_BANK,
                "987654321",
                "MPESA",
                Currency.KES
        );

        when(beneficiaryRepository.findByIdAndUserId(beneficiaryId, currentUser.getId()))
                .thenReturn(Optional.of(beneficiary));
        when(beneficiaryRepository.existsByUserIdAndPayoutProviderAndAccountNumber(
                currentUser.getId(), PayoutProvider.EQUITY_BANK, "987654321"
        )).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> beneficiaryService.updateBeneficiary(currentUser, beneficiaryId, request));

        assertTrue(ex.getMessage().contains("already saved a beneficiary with this exact account number and provider"));
        verify(beneficiaryRepository, never()).save(any());
    }

    @Test
    void updateBeneficiary_NotFoundOrUnauthorized_ThrowsException() {
        BeneficiaryRequest request = new BeneficiaryRequest(
                "Alice",
                "Smith",
                BeneficiaryType.INDIVIDUAL,
                "alice@example.com",
                "+254711223344",
                "KE",
                "Nairobi",
                PayoutMethod.MOBILE_MONEY,
                PayoutProvider.MPESA,
                "254711223344",
                "MPESA",
                Currency.KES
        );

        when(beneficiaryRepository.findByIdAndUserId(beneficiaryId, currentUser.getId()))
                .thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> beneficiaryService.updateBeneficiary(currentUser, beneficiaryId, request));

        assertTrue(ex.getMessage().contains("Beneficiary not found or unauthorized"));
        verify(beneficiaryRepository, never()).save(any());
    }
}
