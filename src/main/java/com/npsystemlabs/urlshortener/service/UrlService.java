package com.npsystemlabs.urlshortener.service;

import org.springframework.stereotype.Service;
import java.security.SecureRandom;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class UrlService {
    private static final String ALPHA =
        "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private final ConcurrentHashMap<String, String> store = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    public String shorten(String longUrl) {
        for (int attempt = 0; attempt < 5; attempt++) {
            String code = generateCode();
            if (store.putIfAbsent(code, longUrl) == null) return code;
        }
        throw new IllegalStateException("Could not generate unique short code");
    }

    public Optional<String> resolve(String code) {
        return Optional.ofNullable(store.get(code));
    }

    private String generateCode() {
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) sb.append(ALPHA.charAt(random.nextInt(ALPHA.length())));
        return sb.toString();
    }
}
