# URL Shortener — Architecture

## Overview

**Stack:** Spring Boot 3.4.1 + Java 21, no database — pure in-memory storage.

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
ConcurrentHashMap      (in-memory store: shortCode → longUrl)
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

- **`shorten(longUrl)`** — generates a random 6-character alphanumeric code (62 possible chars), tries up to 5 times to find a collision-free slot, then stores `code → longUrl` in the map.
- **`resolve(code)`** — looks up the code and returns the long URL (as an `Optional`).
- Uses `ConcurrentHashMap` with `putIfAbsent` for thread-safe writes.

### 3. Model records

- `CreateUrlRequest(String longUrl)` — request body
- `CreateUrlResponse(String shortCode, String shortUrl)` — response body

---

## Request Flow

### Shortening a URL

```
POST /api/v1/urls  { "longUrl": "https://example.com/very/long/path" }
  → Controller validates input (not blank)
  → Calls service.shorten()
  → Service generates 6-char code, stores in map
  → Returns 201 Created: { "shortCode": "aB3xYz", "shortUrl": "http://localhost:8080/aB3xYz" }
```

### Redirecting

```
GET /aB3xYz
  → Controller calls service.resolve("aB3xYz")
  → Map lookup returns "https://example.com/very/long/path"
  → Returns 302 Found with Location header → browser follows redirect
  → If not found → 404
```

---

## Key Design Decisions & Limitations

| Aspect | Current design | Implication |
|--------|---------------|-------------|
| Storage | In-memory `ConcurrentHashMap` | All data lost on restart |
| Code generation | Random 6-char (62^6 ≈ 56B combinations) | Collision chance is very low |
| Collision handling | Retry up to 5 times | Throws if all 5 fail (extremely unlikely) |
| Base URL | Hardcoded `localhost:8080` | Not configurable via properties |
| No persistence | No DB dependency | Simple but not production-ready |
