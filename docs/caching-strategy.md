# Caching Strategy

## Problem
Added `@Cacheable` and `@CachePut` annotations to `UrlService` to cache URL lookups in Redis.
Cache was wired up correctly (Spring Boot + Upstash Redis), but no keys were ever written —
`GET /{shortCode}` kept hitting Postgres on every request.

## Root Cause
Spring Cache works via proxy-based AOP. When a Spring bean calls its own methods internally
(`this.warmCache(...)`, `this.resolveFromCache(...)`), the call bypasses the proxy entirely —
so the cache annotations never fire.

Both cache operations in the original `UrlService` were self-invocations:
- `shorten()` called `warmCache()` directly → `@CachePut` never executed
- `resolve()` called `resolveFromCache()` directly → `@Cacheable` never executed

## Solution
Replaced annotation-based caching with direct `CacheManager` injection inside a dedicated
`RedisCacheService` bean. No proxy involved for the cache reads/writes themselves — they happen
explicitly. The separate bean boundary is what makes `@CircuitBreaker` work (see below).

```java
// RedisCacheService.java
private Cache cache() {
    return cacheManager.getCache("urls");
}
```

## Flow

```
POST /api/v1/urls
  → save to Postgres
  → RedisCacheService.put(code, longUrl)   ← best-effort pre-warm via circuit breaker
      Redis up:   cache written
      Redis down: no-op fallback, POST still returns 201

GET /{shortCode}
  → RedisCacheService.get(code)            ← via circuit breaker
  → Circuit CLOSED + hit  → return longUrl (no DB query)
  → Circuit CLOSED + miss → fetch from Postgres → put to Redis → return longUrl
  → Circuit OPEN          → skip Redis → fetch from Postgres → return longUrl
```

## Why This Approach
Direct `CacheManager` usage is more explicit and bypasses proxy limitations entirely.
It also makes the caching logic easier to follow — no annotation magic, no hidden behavior.

The same AOP self-invocation problem that ruled out `@Cacheable` also applies to
`@CircuitBreaker`. Extracting Redis operations into `RedisCacheService` solves both:
`UrlService` calls through the Spring proxy, so both the cache and the circuit breaker fire correctly.

## Circuit Breaker
`RedisCacheService.get()` and `put()` are both annotated with `@CircuitBreaker(name = "redis")`.
When Redis fails repeatedly (≥50% of last 10 calls), the circuit opens and all subsequent calls
return the fallback immediately without attempting a Redis connection — protecting the app from
hammering a dead host. After 30s the circuit moves to HALF_OPEN and probes for recovery.

State is visible at `/actuator/health` under `components.circuitBreakers.details.redis`.
