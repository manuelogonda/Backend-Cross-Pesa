package com.manuelorg.cross_pesa.payment.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FlutterwaveInitRequest {
    private String tx_ref;
    private String amount;
    private String currency;
    private String redirect_url;
    private Customer customer;
    private Customizations customizations;

    @Data
    @Builder
    public static class Customer {
        private String email;
        private String name;
    }

    @Data
    @Builder
    public static class Customizations {
        private String title;
        private String description;
    }
}
