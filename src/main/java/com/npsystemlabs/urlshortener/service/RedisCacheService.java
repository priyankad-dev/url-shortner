package com.npsystemlabs.urlshortener.service;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

@Service
public class RedisCacheService {
    private static final Logger log = LoggerFactory.getLogger(RedisCacheService.class);
    private final CacheManager cacheManager;

    public RedisCacheService(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @CircuitBreaker(name = "redis", fallbackMethod = "getFallback")
    public String get(String code) {
        Cache.ValueWrapper w = cache().get(code);
        return w != null ? (String) w.get() : null;
    }

    public String getFallback(String code, Throwable t) {
        if (t instanceof CallNotPermittedException) {
            log.debug("Redis circuit OPEN — cache miss for code={}", code);
        } else {
            log.warn("Redis get failed ({}) — cache miss for code={}", t.getMessage(), code);
        }
        return null;
    }

    @CircuitBreaker(name = "redis", fallbackMethod = "putFallback")
    public void put(String code, String longUrl) {
        cache().put(code, longUrl);
    }

    public void putFallback(String code, String longUrl, Throwable t) {
        if (!(t instanceof CallNotPermittedException)) {
            log.warn("Redis put failed ({}) — skipping cache write for code={}", t.getMessage(), code);
        }
    }

    private Cache cache() {
        return cacheManager.getCache("urls");
    }
}