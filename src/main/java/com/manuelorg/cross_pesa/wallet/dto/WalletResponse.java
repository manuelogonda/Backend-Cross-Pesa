package com.manuelorg.cross_pesa.wallet.dto;

import com.manuelorg.cross_pesa.wallet.entity.Wallet;
import com.manuelorg.cross_pesa.wallet.enums.Currency;
import com.manuelorg.cross_pesa.wallet.enums.WalletType;

import java.math.BigDecimal;
import java.util.UUID;

public record WalletResponse(
        UUID id,
        Currency currency,
        WalletType walletType,
        BigDecimal balance,
        BigDecimal lockedBalance,
        BigDecimal availableBalance,
        String status
) {
    public static WalletResponse fromEntity(Wallet wallet) {
        return new WalletResponse(
                wallet.getId(),
                wallet.getCurrency(),
                wallet.getWalletType(),
                wallet.getBalance(),
                wallet.getLockedBalance(),
                wallet.getAvailableBalance(),
                wallet.getStatus().name()
        );
    }
}
