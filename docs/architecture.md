# URL Shortener — Architecture

## Overview

**Stack:** Spring Boot 3.4.1 + Java 21, Spring Data JPA, Postgres (Neon), Redis (Upstash).

```
Client
  │
  ▼
UrlController          (HTTP layer)
  │
  ▼
UrlService             (business logic + cache coordination)
  │         │
  │         ▼
  │       Redis / Upstash   (read cache: TTL 24h)
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

- **`shorten(longUrl)`** — generates a random 6-character alphanumeric code (62 possible chars), saves via `UrlRepository` (retries on `DataIntegrityViolationException`), then pre-warms Redis so the first redirect is a cache hit.
- **`resolve(code)`** — checks Redis first; on a miss fetches from Postgres, populates Redis, and returns the URL. Returns empty `Optional` if not found.

Caching uses `CacheManager` directly rather than `@Cacheable`/`@CachePut` annotations — see `docs/caching-strategy.md` for why.

### 3. `UrlRepository` — Data access

Spring Data JPA repository on top of `UrlEntity`. Provides `save` and `findByShortCode(String)`.

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
  → Service generates 6-char code, saves to Postgres (retries on collision)
  → Service writes to Redis cache (pre-warm)
  → Returns 201 Created: { "shortCode": "aB3xYz", "shortUrl": "http://localhost:8080/aB3xYz" }
```

### Redirecting

```
GET /aB3xYz
  → Service checks Redis for key "urls::aB3xYz"
  → Cache hit  → return longUrl immediately (no DB query)
  → Cache miss → fetch from Postgres → write to Redis → return longUrl
  → Returns 302 Found with Location header → browser follows redirect
  → If not found in Redis or Postgres → 404
```

---

## Key Design Decisions & Limitations

| Aspect | Current design | Implication |
|--------|---------------|-------------|
| Storage | Postgres via Neon (Spring Data JPA) | Source of truth; data survives restarts |
| Cache | Redis via Upstash (TTL 24h) | Reduces Postgres load on repeated lookups |
| Cache key format | `urls::<shortCode>` | Set by Spring's `RedisCacheManager` |
| Cache implementation | Direct `CacheManager` (not annotations) | Avoids Spring AOP self-invocation limitation |
| Code generation | Random 6-char (62^6 ≈ 56B combinations) | Collision chance is very low |
| Collision handling | Retry up to 5 times on `DataIntegrityViolationException` | Throws if all 5 fail (extremely unlikely) |
| Duplicate URLs | Not deduplicated — same long URL gets a new short code each POST | By design; deduplication not in scope |
| Base URL | Hardcoded `localhost:8080` | Not configurable via properties |
| Credentials | Injected via env vars (`DB_URL`, `DB_USER`, `DB_PASSWORD`, `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`) | Never committed to source control |
