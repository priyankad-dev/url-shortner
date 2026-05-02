package com.npsystemlabs.urlshortener.controller;

import com.npsystemlabs.urlshortener.model.CreateUrlRequest;
import com.npsystemlabs.urlshortener.model.CreateUrlResponse;
import com.npsystemlabs.urlshortener.service.UrlService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;

@RestController
public class UrlController {
    private final UrlService service;

    public UrlController(UrlService service) { this.service = service; }

    @PostMapping("/api/v1/urls")
    public ResponseEntity<CreateUrlResponse> create(@RequestBody CreateUrlRequest req) {
        if (req.longUrl() == null || req.longUrl().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        String code = service.shorten(req.longUrl());
        String shortUrl = "http://localhost:8080/" + code;
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(new CreateUrlResponse(code, shortUrl));
    }

    @GetMapping("/{shortCode}")
    public ResponseEntity<Void> redirect(@PathVariable String shortCode) {
        return service.resolve(shortCode)
            .map(longUrl -> ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(longUrl))
                .<Void>build())
            .orElse(ResponseEntity.notFound().build());
    }
}
