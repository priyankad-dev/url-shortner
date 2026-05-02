# URL Shortener

A minimal URL shortener REST API built with Spring Boot 3 and Java 21.

## Tech Stack

- Java 21
- Spring Boot 3.4.1
- Spring Web, Spring Actuator
- In-memory storage (`ConcurrentHashMap`)

## Getting Started

### Prerequisites

- Java 21+
- Maven 3.x

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
- Storage is in-memory — all mappings are lost on restart.
- See [`docs/architecture.md`](.docs/architecture.md) for a full architecture breakdown.
