package com.manuelorg.cross_pesa.auth.security;

import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

@Service
public class LoginRateLimiterService {

    private final RedissonClient redissonClient;

    public LoginRateLimiterService(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    /**
     * Evaluates if the IP has tokens left.
     * @return true if the token was consumed, false if the rate limit is exceeded.
     */
    public boolean tryConsume(String ip) {
        // 1. Ask Redis for the rate limiter specific to this IP
        RRateLimiter rateLimiter = redissonClient.getRateLimiter("login_limiter:" + ip);

        // 2. Set the rule: 5 tokens total, refills every 15 minutes.
        // (trySetRate only applies if the rule hasn't been set yet, so it's safe to call here)
        rateLimiter.trySetRate(RateType.OVERALL, 5, 15, RateIntervalUnit.MINUTES);

        // 3. Try to consume 1 token. Returns true if allowed, false if empty.
        return rateLimiter.tryAcquire(1);
    }
}