# URL Shortener — Architecture

## Overview

**Stack:** Spring Boot 3.4.1 + Java 21, Spring Data JPA, Postgres (Neon), Redis (Upstash), Resilience4j.

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
RedisCacheService      (cache ops — circuit breaker here)
  │         │
  │ CLOSED  │ OPEN (circuit tripped)
  ▼         ▼
Redis     (skip)
(Upstash)
  │
  │ cache miss / circuit open
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

- **`shorten(longUrl)`** — generates a random 6-character alphanumeric code (62 possible chars), saves via `UrlRepository` (retries on `DataIntegrityViolationException`), then pre-warms the cache via `RedisCacheService` (best-effort — a Redis failure skips the warm without failing the POST).
- **`resolve(code)`** — delegates cache lookup to `RedisCacheService`; on a miss (or open circuit) fetches from Postgres, attempts to repopulate the cache, and returns the URL. Returns empty `Optional` if not found.

### 3. `RedisCacheService` — Cache operations + circuit breaker

Owns all Redis reads and writes. Each operation is annotated with `@CircuitBreaker(name = "redis")`:

- **`get(code)`** — reads from the `urls` cache. Fallback returns `null` (signals a cache miss to the caller).
- **`put(code, longUrl)`** — writes to the `urls` cache. Fallback is a no-op log line.

Extracted into its own bean (rather than inlined in `UrlService`) to avoid the Spring AOP self-invocation trap — `@CircuitBreaker` only fires on cross-bean calls through the proxy. See `docs/caching-strategy.md` for the full explanation.

**Circuit breaker configuration (`name = "redis"`):**

| Setting | Value | Effect |
|---|---|---|
| `sliding-window-size` | 10 | Tracks last 10 calls |
| `failure-rate-threshold` | 50% | Opens circuit when ≥50% of calls fail |
| `wait-duration-in-open-state` | 30s | Waits 30s before probing recovery |
| `permitted-number-of-calls-in-half-open-state` | 3 | 3 probe calls to confirm Redis recovered |

Circuit state is visible at `/actuator/health`.

### 4. `UrlRepository` — Data access

Spring Data JPA repository on top of `UrlEntity`. Provides `save` and `findByShortCode(String)`.

### 5. `UrlEntity` — Persistence model

JPA entity mapped to the `urls` table:

| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGINT | auto-generated PK |
| `short_code` | VARCHAR(10) | unique, not null |
| `long_url` | TEXT | not null |
| `created_at` | TIMESTAMPTZ | defaults to `Instant.now()` |

### 6. Model records

- `CreateUrlRequest(String longUrl)` — request body
- `CreateUrlResponse(String shortCode, String shortUrl)` — response body

---

## Request Flow

### Shortening a URL

```
POST /api/v1/urls  { "longUrl": "https://example.com/very/long/path" }
  → Controller validates input (not blank)
  → Service generates 6-char code, saves to Postgres (retries on collision)
  → RedisCacheService.put() pre-warms cache (best-effort via circuit breaker)
      → Redis up:   cache written, first redirect is a cache hit
      → Redis down: fallback no-ops, shorten still returns 201
  → Returns 201 Created: { "shortCode": "aB3xYz", "shortUrl": "http://localhost:8080/aB3xYz" }
```

### Redirecting

```
GET /aB3xYz
  → RedisCacheService.get() checks Redis (via circuit breaker)
      → Circuit CLOSED + cache hit  → return longUrl immediately (no DB query)
      → Circuit CLOSED + cache miss → fetch from Postgres → write to Redis → return longUrl
      → Circuit OPEN               → skip Redis entirely → fetch from Postgres → return longUrl
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
| Cache implementation | Direct `CacheManager` in `RedisCacheService` (not annotations) | Avoids Spring AOP self-invocation limitation |
| Circuit breaker | Resilience4j on `RedisCacheService` (50% threshold, 30s open) | Redis outage degrades to Postgres; service never returns 5xx for redirect |
| Redis failure on shorten | Cache pre-warm is best-effort; falls back to no-op | POST always succeeds; first redirect after outage hits DB instead of cache |
| Code generation | Random 6-char (62^6 ≈ 56B combinations) | Collision chance is very low |
| Collision handling | Retry up to 5 times on `DataIntegrityViolationException` | Throws if all 5 fail (extremely unlikely) |
| Duplicate URLs | Not deduplicated — same long URL gets a new short code each POST | By design; deduplication not in scope |
| Base URL | Hardcoded `localhost:8080` | Not configurable via properties |
| Credentials | Injected via env vars (`DB_URL`, `DB_USER`, `DB_PASSWORD`, `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`) | Never committed to source control |
