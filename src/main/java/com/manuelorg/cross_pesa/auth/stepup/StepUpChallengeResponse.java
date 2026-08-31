package com.manuelorg.cross_pesa.auth.stepup;

import java.time.OffsetDateTime;
import java.util.UUID;

public record StepUpChallengeResponse(
        UUID challengeId,
        OffsetDateTime expiresAt,
        String delivery
) {}
