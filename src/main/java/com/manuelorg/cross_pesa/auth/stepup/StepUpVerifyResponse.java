package com.manuelorg.cross_pesa.auth.stepup;

import java.time.OffsetDateTime;

public record StepUpVerifyResponse(
        String stepUpToken,
        OffsetDateTime expiresAt
) {}
