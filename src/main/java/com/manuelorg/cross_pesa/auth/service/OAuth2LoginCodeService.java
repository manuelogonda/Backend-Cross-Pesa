package com.manuelorg.cross_pesa.auth.service;

import com.manuelorg.cross_pesa.auth.entity.User;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OAuth2LoginCodeService {

    private static final Duration CODE_TTL = Duration.ofMinutes(2);
    private static final String CODE_KEY_PREFIX = "oauth2_login_code:";

    private final RedissonClient redissonClient;

    public String issueCode(User user) {
        String code = UUID.randomUUID().toString().replace("-", "");
        RBucket<String> bucket = redissonClient.getBucket(CODE_KEY_PREFIX + code);
        bucket.set(user.getEmail(), CODE_TTL);
        return code;
    }

    public String consumeCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        RBucket<String> bucket = redissonClient.getBucket(CODE_KEY_PREFIX + code.trim());
        return bucket.getAndDelete();
    }
}
