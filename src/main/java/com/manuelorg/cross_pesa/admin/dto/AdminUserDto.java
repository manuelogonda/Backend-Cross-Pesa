package com.manuelorg.cross_pesa.admin.dto;

import com.manuelorg.cross_pesa.auth.entity.KycStatus;
import com.manuelorg.cross_pesa.auth.entity.User;
import com.manuelorg.cross_pesa.auth.entity.UserStatus;
import com.manuelorg.cross_pesa.wallet.dto.WalletResponse;
import com.manuelorg.cross_pesa.wallet.enums.WalletStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

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
            String idNumberMasked,
            UserStatus status,
            KycStatus kycStatus,
            Integer kycLevel,
            OffsetDateTime createdAt
    ) {
        public static AdminUserResponse fromEntity(User user) {
            return new AdminUserResponse(
                    user.getId(),
                    user.getFirstName(),
                    user.getLastName(),
                    user.getEmail(),
                    user.getPhoneNumber(),
                    user.getIdType(),
                    maskIdNumber(user.getIdNumber()),
                    user.getStatus(),
                    user.getKycStatus(),
                    user.getKycLevel(),
                    user.getCreatedAt()
            );
        }

        /**
         * List views must not expose full government ID numbers.
         * Keeps only the last 3 characters for reference matching.
         */
        private static String maskIdNumber(String idNumber) {
            if (idNumber == null || idNumber.length() <= 3) {
                return "***";
            }
            return "*".repeat(idNumber.length() - 3) + idNumber.substring(idNumber.length() - 3);
        }
    }

    public record UpdateStatusRequest(
            @NotNull(message = "Wallet status is required")
            WalletStatus status,

            @NotBlank(message = "Reason is required for audit")
            String reason
    ) {}

    public record UpdateKycRequest(
            @NotNull(message = "KYC status is required")
            KycStatus kycStatus,

            @NotNull(message = "KYC level is required")
            @Positive(message = "KYC level must be positive")
            Integer kycLevel,

            @NotBlank(message = "Admin notes are required for audit")
            String adminNotes
    ) {}

    public record AdminWalletStatusResponse(
            String message,
            WalletResponse wallet
    ) {}
}
