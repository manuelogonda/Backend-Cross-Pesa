package com.manuelorg.cross_pesa.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedisConfig {
    @Value("${app.redis.password}")
    private String redisPassword;

    @Value("${app.redis.address}")
    private String redisAddress;

    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        // Assuming your local Docker Redis is running on default port 6379
        config.useSingleServer()
                .setAddress(redisAddress)
                .setPassword(redisPassword);;
        return Redisson.create(config);
    }
}
