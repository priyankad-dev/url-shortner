package com.npsystemlabs.urlshortener.service;

import com.npsystemlabs.urlshortener.model.UrlEntity;
import com.npsystemlabs.urlshortener.repository.UrlRepository;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Optional;

@Service
public class UrlService {
    private static final String ALPHA =
        "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private final UrlRepository repo;
    private final CacheManager cacheManager;
    private final SecureRandom random = new SecureRandom();

    public UrlService(UrlRepository repo, CacheManager cacheManager) {
        this.repo = repo;
        this.cacheManager = cacheManager;
    }

    public String shorten(String longUrl) {
        for (int attempt = 0; attempt < 5; attempt++) {
            String code = generateCode();
            try {
                repo.save(new UrlEntity(code, longUrl));
                urlsCache().put(code, longUrl);
                return code;
            } catch (DataIntegrityViolationException collision) {
                // retry on short code collision
            }
        }
        throw new IllegalStateException("Could not generate unique short code");
    }

    public Optional<String> resolve(String code) {
        Cache.ValueWrapper cached = urlsCache().get(code);
        if (cached != null) return Optional.ofNullable((String) cached.get());

        return repo.findByShortCode(code).map(entity -> {
            urlsCache().put(code, entity.getLongUrl());
            return entity.getLongUrl();
        });
    }

    private Cache urlsCache() {
        return cacheManager.getCache("urls");
    }

    private String generateCode() {
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) sb.append(ALPHA.charAt(random.nextInt(ALPHA.length())));
        return sb.toString();
    }
}
