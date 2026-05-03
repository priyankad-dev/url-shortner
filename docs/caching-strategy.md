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
Replaced annotation-based caching with direct `CacheManager` injection. No proxy involved —
cache reads and writes happen explicitly in the same methods that own the logic.

```java
private Cache urlsCache() {
    return cacheManager.getCache("urls");
}
```

## Flow

```
POST /api/v1/urls
  → save to Postgres
  → urlsCache().put(code, longUrl)   ← cache pre-warmed immediately

GET /{shortCode}
  → urlsCache().get(code)
  → hit  → return longUrl (no DB query)
  → miss → fetch from Postgres → urlsCache().put(code, longUrl) → return longUrl
```

## Why This Approach
Direct `CacheManager` usage is more explicit and bypasses proxy limitations entirely.
It also makes the caching logic easier to follow — no annotation magic, no hidden behavior.

## Future Improvements
- Add Resilience4j circuit breaker so a Redis outage degrades gracefully to Postgres
  instead of returning 500
- Consider a separate `CachingUrlService` wrapper if annotation-based caching is preferred
