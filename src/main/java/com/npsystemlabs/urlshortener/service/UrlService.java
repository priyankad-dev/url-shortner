package com.npsystemlabs.urlshortener.service;

import com.npsystemlabs.urlshortener.model.UrlEntity;
import com.npsystemlabs.urlshortener.repository.UrlRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Optional;

@Service
public class UrlService {
    private static final String ALPHA =
        "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private final UrlRepository repo;
    private final SecureRandom random = new SecureRandom();

    public UrlService(UrlRepository repo) { this.repo = repo; }

    public String shorten(String longUrl) {
        for (int attempt = 0; attempt < 5; attempt++) {
            String code = generateCode();
            try {
                repo.save(new UrlEntity(code, longUrl));
                return code;
            } catch (DataIntegrityViolationException collision) {
                // retry on short code collision
            }
        }
        throw new IllegalStateException("Could not generate unique short code");
    }

    public Optional<String> resolve(String code) {
        return repo.findByShortCode(code).map(UrlEntity::getLongUrl);
    }

    private String generateCode() {
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) sb.append(ALPHA.charAt(random.nextInt(ALPHA.length())));
        return sb.toString();
    }
}
