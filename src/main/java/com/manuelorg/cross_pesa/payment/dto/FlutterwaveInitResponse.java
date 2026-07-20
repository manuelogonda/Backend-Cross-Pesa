package com.manuelorg.cross_pesa.payment.dto;

import lombok.Data;

@Data
public class FlutterwaveInitResponse {
    private String status;
    private String message;
    private ResponseData data;

    @Data
    public static class ResponseData {
        private String link; // This is the magical payment URL!
    }
}
