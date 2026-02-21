package com.agw.auth.config;

import java.util.List;
import java.util.concurrent.TimeUnit;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class CacheConfig {

    public static final String USER_BY_USERNAME = "USER_BY_USERNAME_";
    public static final String CLIENT_BY_CLIENT_ID = "CLIENT_BY_CLIENT_ID_";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCache userByUsernameCache = new CaffeineCache(
            USER_BY_USERNAME,
            Caffeine.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .maximumSize(1_000)
                .build()
        );

        CaffeineCache clientByClientIdCache = new CaffeineCache(
            CLIENT_BY_CLIENT_ID,
            Caffeine.newBuilder()
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .maximumSize(1_000)
                .build()
        );

        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(List.of(userByUsernameCache, clientByClientIdCache));
        return manager;
    }
}
