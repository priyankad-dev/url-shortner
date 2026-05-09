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
    private static final String NOT_FOUND_SENTINEL = "__NF__";

    private final CacheManager cacheManager;

    public RedisCacheService(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    // ── positive cache (urls, TTL 24h) ────────────────────────────────────────

    @CircuitBreaker(name = "redis", fallbackMethod = "getFallback")
    public String get(String code) {
        Cache.ValueWrapper w = urlsCache().get(code);
        return w != null ? (String) w.get() : null;
    }

    public String getFallback(String code, Throwable t) {
        if (!(t instanceof CallNotPermittedException)) {
            log.warn("Redis get failed ({}) — cache miss for code={}", t.getMessage(), code);
        } else {
            log.debug("Redis circuit OPEN — cache miss for code={}", code);
        }
        return null;
    }

    @CircuitBreaker(name = "redis", fallbackMethod = "putFallback")
    public void put(String code, String longUrl) {
        urlsCache().put(code, longUrl);
    }

    public void putFallback(String code, String longUrl, Throwable t) {
        if (!(t instanceof CallNotPermittedException)) {
            log.warn("Redis put failed ({}) — skipping cache write for code={}", t.getMessage(), code);
        }
    }

    // ── negative cache (urls-not-found, TTL 60s) ──────────────────────────────

    @CircuitBreaker(name = "redis", fallbackMethod = "isNotFoundFallback")
    public boolean isNotFound(String code) {
        Cache.ValueWrapper w = notFoundCache().get(code);
        return w != null && NOT_FOUND_SENTINEL.equals(w.get());
    }

    public boolean isNotFoundFallback(String code, Throwable t) {
        if (!(t instanceof CallNotPermittedException)) {
            log.warn("Redis isNotFound failed ({}) for code={}", t.getMessage(), code);
        }
        return false;   // safe default: let it fall through to Postgres
    }

    @CircuitBreaker(name = "redis", fallbackMethod = "putNotFoundFallback")
    public void putNotFound(String code) {
        notFoundCache().put(code, NOT_FOUND_SENTINEL);
    }

    public void putNotFoundFallback(String code, Throwable t) {
        if (!(t instanceof CallNotPermittedException)) {
            log.warn("Redis putNotFound failed ({}) for code={}", t.getMessage(), code);
        }
    }

    // ── private helpers ────────────────────────────────────────────────────────

    private Cache urlsCache() {
        return cacheManager.getCache("urls");
    }

    private Cache notFoundCache() {
        return cacheManager.getCache("urls-not-found");
    }
}
