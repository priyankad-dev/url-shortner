# URL Shortener

A minimal URL shortener REST API built with Spring Boot 3 and Java 21.

## Tech Stack

- Java 21
- Spring Boot 3.4.1
- Spring Web, Spring Data JPA, Spring Cache, Spring Actuator
- Postgres (Neon) — persistent store
- Redis (Upstash) — read cache (TTL 24h)

## Getting Started

### Prerequisites

- Java 21+
- Maven 3.x
- A Postgres instance (e.g. [Neon](https://neon.tech) free tier)
- A Redis instance (e.g. [Upstash](https://upstash.com) free tier)

### Environment Variables

Set these in your run configuration before starting the app:

```
DB_URL=jdbc:postgresql://<host>/<db>?sslmode=require
DB_USER=<user>
DB_PASSWORD=<password>
REDIS_HOST=<host>.upstash.io
REDIS_PORT=6379
REDIS_PASSWORD=<password>
REDIS_SSL=true
```

### Run

```bash
./mvnw spring-boot:run
```

The server starts at `http://localhost:8080`.

## API

### Shorten a URL

```
POST /api/v1/urls
Content-Type: application/json

{ "longUrl": "https://example.com/very/long/path" }
```

**Response** `201 Created`

```json
{
  "shortCode": "aB3xYz",
  "shortUrl": "http://localhost:8080/aB3xYz"
}
```

### Redirect

```
GET /{shortCode}
```

Returns `302 Found` with a `Location` header pointing to the original URL, or `404` if the code doesn't exist.

## Notes

- Short codes are 6-character random alphanumeric strings (62^6 ≈ 56 billion combinations).
- Data is persisted in Postgres — mappings survive restarts.
- Redis is required — the app will error on requests if Redis is unreachable.
- See [`docs/architecture.md`](docs/architecture.md) for a full architecture breakdown.
- See [`docs/caching-strategy.md`](docs/caching-strategy.md) for caching design decisions.
