package com.manuelorg.cross_pesa.admin.dto;

import com.manuelorg.cross_pesa.auth.entity.KycStatus;
import com.manuelorg.cross_pesa.auth.entity.UserStatus;
import com.manuelorg.cross_pesa.wallet.enums.WalletStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public class AdminUserDto {

    public record AdminUserResponse(
            UUID id,
            String firstName,
            String lastName,
            String email,
            String phoneNumber,
            String idType,
            String idNumber,
            UserStatus status,
            KycStatus kycStatus,
            Integer kycLevel,
            OffsetDateTime createdAt
    ) {}

    public record UpdateStatusRequest(
            WalletStatus status,
            String reason // Keep an audit trail of why an admin suspended someone
    ) {}

    public record UpdateKycRequest(
            KycStatus kycStatus,
            Integer kycLevel,
            String adminNotes
    ) {}
}
