package com.manuelorg.cross_pesa.beneficiaries;

import com.manuelorg.cross_pesa.auth.entity.Role;
import com.manuelorg.cross_pesa.auth.entity.User;
import com.manuelorg.cross_pesa.beneficiaries.controller.BeneficiaryController;
import com.manuelorg.cross_pesa.beneficiaries.dto.BeneficiaryRequest;
import com.manuelorg.cross_pesa.beneficiaries.dto.BeneficiaryResponse;
import com.manuelorg.cross_pesa.beneficiaries.entity.BeneficiaryType;
import com.manuelorg.cross_pesa.beneficiaries.entity.PayoutMethod;
import com.manuelorg.cross_pesa.beneficiaries.entity.PayoutProvider;
import com.manuelorg.cross_pesa.beneficiaries.service.BeneficiaryService;
import com.manuelorg.cross_pesa.wallet.enums.Currency;
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

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BeneficiaryControllerTest {

    @Mock
    private BeneficiaryService beneficiaryService;

    @InjectMocks
    private BeneficiaryController beneficiaryController;

    private Validator validator;
    private User currentUser;
    private BeneficiaryResponse sampleResponse;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();

        currentUser = User.builder()
                .id(UUID.randomUUID())
                .email("user@example.com")
                .firstName("John")
                .lastName("Doe")
                .role(Role.USER)
                .build();

        sampleResponse = new BeneficiaryResponse(
                UUID.randomUUID(),
                "Alice",
                "Smith",
                "INDIVIDUAL",
                "alice@example.com",
                "+254711223344",
                "KE",
                "Nairobi",
                "MOBILE_MONEY",
                "M-PESA",
                "254711223344",
                "MPESA",
                "KES"
        );
    }

    @Test
    void getBeneficiaries_ReturnsOkWithPagedData() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<BeneficiaryResponse> pagedResponse = new PageImpl<>(List.of(sampleResponse));
        when(beneficiaryService.getUserBeneficiaries(currentUser.getId(), pageable)).thenReturn(pagedResponse);

        ResponseEntity<Page<BeneficiaryResponse>> response = beneficiaryController.getBeneficiaries(currentUser, pageable);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().getTotalElements());
        verify(beneficiaryService).getUserBeneficiaries(currentUser.getId(), pageable);
    }

    @Test
    void addBeneficiary_ReturnsCreatedWithResponse() {
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

        when(beneficiaryService.createBeneficiary(currentUser, request)).thenReturn(sampleResponse);

        ResponseEntity<BeneficiaryResponse> response = beneficiaryController.addBeneficiary(currentUser, request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(sampleResponse.id(), response.getBody().id());
        verify(beneficiaryService).createBeneficiary(currentUser, request);
    }

    @Test
    void removeBeneficiary_ReturnsNoContent() {
        UUID id = UUID.randomUUID();

        ResponseEntity<Void> response = beneficiaryController.removeBeneficiary(currentUser, id);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        assertNull(response.getBody());
        verify(beneficiaryService).deleteBeneficiary(currentUser, id);
    }

    @Test
    void updateBeneficiary_ReturnsOkWithUpdatedResponse() {
        UUID id = UUID.randomUUID();
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

        when(beneficiaryService.updateBeneficiary(currentUser, id, request)).thenReturn(sampleResponse);

        ResponseEntity<BeneficiaryResponse> response = beneficiaryController.updateBeneficiary(currentUser, id, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(sampleResponse.id(), response.getBody().id());
        verify(beneficiaryService).updateBeneficiary(currentUser, id, request);
    }

    @Test
    void beneficiaryRequest_Validation_InvalidEmail_Fails() {
        BeneficiaryRequest invalid = new BeneficiaryRequest(
                "Alice",
                "Smith",
                BeneficiaryType.INDIVIDUAL,
                "invalid-email",
                "+254711223344",
                "KE",
                "Nairobi",
                PayoutMethod.MOBILE_MONEY,
                PayoutProvider.MPESA,
                "254711223344",
                "MPESA",
                Currency.KES
        );

        var violations = validator.validate(invalid);
        assertFalse(violations.isEmpty());
    }

    @Test
    void beneficiaryRequest_Validation_InvalidCountryCode_Fails() {
        BeneficiaryRequest invalid = new BeneficiaryRequest(
                "Alice",
                "Smith",
                BeneficiaryType.INDIVIDUAL,
                "alice@example.com",
                "+254711223344",
                "KENYA",
                "Nairobi",
                PayoutMethod.MOBILE_MONEY,
                PayoutProvider.MPESA,
                "254711223344",
                "MPESA",
                Currency.KES
        );

        var violations = validator.validate(invalid);
        assertFalse(violations.isEmpty());
    }

    @Test
    void beneficiaryRequest_Validation_Valid_Passes() {
        BeneficiaryRequest valid = new BeneficiaryRequest(
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

        var violations = validator.validate(valid);
        assertTrue(violations.isEmpty());
    }
}
