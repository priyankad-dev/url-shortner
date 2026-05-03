# URL Shortener — Architecture

## Overview

**Stack:** Spring Boot 3.4.1 + Java 21, Spring Data JPA, Postgres (Neon).

```
Client
  │
  ▼
UrlController          (HTTP layer)
  │
  ▼
UrlService             (business logic)
  │
  ▼
UrlRepository          (Spring Data JPA)
  │
  ▼
Postgres / Neon        (persistent store: urls table)
```

---

## Components

### 1. `UrlController` — HTTP layer

Two endpoints:

| Method | Path | Purpose |
|--------|------|---------|
| `POST` | `/api/v1/urls` | Accepts a long URL, returns a short code |
| `GET` | `/{shortCode}` | Redirects to the original long URL |

### 2. `UrlService` — Business logic

- **`shorten(longUrl)`** — generates a random 6-character alphanumeric code (62 possible chars), tries up to 5 times to save via the repo; retries on `DataIntegrityViolationException` (short code collision).
- **`resolve(code)`** — delegates to `UrlRepository.findByShortCode`, returns the long URL as an `Optional`.

### 3. `UrlRepository` — Data access

Spring Data JPA repository on top of `UrlEntity`. Provides `save` and `findByShortCode(String)` out of the box.

### 4. `UrlEntity` — Persistence model

JPA entity mapped to the `urls` table:

| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGINT | auto-generated PK |
| `short_code` | VARCHAR(10) | unique, not null |
| `long_url` | TEXT | not null |
| `created_at` | TIMESTAMPTZ | defaults to `Instant.now()` |

### 5. Model records

- `CreateUrlRequest(String longUrl)` — request body
- `CreateUrlResponse(String shortCode, String shortUrl)` — response body

---

## Request Flow

### Shortening a URL

```
POST /api/v1/urls  { "longUrl": "https://example.com/very/long/path" }
  → Controller validates input (not blank)
  → Calls service.shorten()
  → Service generates 6-char code, saves via UrlRepository (retries on collision)
  → Returns 201 Created: { "shortCode": "aB3xYz", "shortUrl": "http://localhost:8080/aB3xYz" }
```

### Redirecting

```
GET /aB3xYz
  → Controller calls service.resolve("aB3xYz")
  → UrlRepository.findByShortCode returns "https://example.com/very/long/path"
  → Returns 302 Found with Location header → browser follows redirect
  → If not found → 404
```

---

## Key Design Decisions & Limitations

| Aspect | Current design | Implication |
|--------|---------------|-------------|
| Storage | Postgres via Neon (Spring Data JPA) | Data survives restarts |
| Code generation | Random 6-char (62^6 ≈ 56B combinations) | Collision chance is very low |
| Collision handling | Retry up to 5 times on `DataIntegrityViolationException` | Throws if all 5 fail (extremely unlikely) |
| Base URL | Hardcoded `localhost:8080` | Not configurable via properties |
| DB credentials | Injected via env vars (`DB_URL`, `DB_USER`, `DB_PASSWORD`) | Never committed to source control |
