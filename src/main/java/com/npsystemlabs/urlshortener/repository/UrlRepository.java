package com.npsystemlabs.urlshortener.repository;

import com.npsystemlabs.urlshortener.model.UrlEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UrlRepository extends JpaRepository<UrlEntity, Long> {
    Optional<UrlEntity> findByShortCode(String shortCode);
}
