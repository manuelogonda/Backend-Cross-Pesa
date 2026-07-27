package com.manuelorg.cross_pesa.wallet.dto;

import com.manuelorg.cross_pesa.wallet.entity.Wallet;
import com.manuelorg.cross_pesa.wallet.enums.Currency;

import java.math.BigDecimal;
import java.util.UUID;

public record WalletResponse(
        UUID id,
        Currency currency,
        BigDecimal balance,
        BigDecimal lockedBalance,
        BigDecimal availableBalance,
        String status
) {
    // A clean static factory method to map an Entity -> DTO
    public static WalletResponse fromEntity(Wallet wallet) {
        return new WalletResponse(
                wallet.getId(),
                wallet.getCurrency(),
                wallet.getBalance(),
                wallet.getLockedBalance(),
                wallet.getAvailableBalance(), // Uses the helper method from your Entity!
                wallet.getStatus().name()
        );
    }
}
