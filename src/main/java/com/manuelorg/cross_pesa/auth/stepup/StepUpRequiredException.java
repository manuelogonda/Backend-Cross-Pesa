package com.manuelorg.cross_pesa.auth.stepup;

public class StepUpRequiredException extends RuntimeException {
    public StepUpRequiredException(String message) {
        super(message);
    }
}
