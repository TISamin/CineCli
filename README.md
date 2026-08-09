# CinemaSeat

Cinema ticket-booking system. Modular Spring Boot monolith + PostgreSQL.
Never double-books a seat, even under 100+ concurrent buyers fighting for the same one.

## One-sentence summary

> CinemaSeat uses a modular Spring Boot application with PostgreSQL transactions and
> row-level locking to guarantee one owner per show-seat, while asynchronous payment
> callbacks and idempotency make the unreliable gateway safe to integrate.

## Architecture

```
                  Browser
                     │
                     ▼
        ┌────────────────────────┐
        │   Spring Boot REST API │
        └──┬───────────┬─────────┘
           │           │
     ┌─────┴───┐  ┌────┴─────┐  ┌────────────┐
     │ Movies  │  │ Booking  │  │  Gateway   │
     │ Shows   │  │ Payment  │  │  client    │
     │ Seats   │  │ Callback │  └─────┬──────┘
     └────┬────┘  └────┬─────┘        │
          └──────┬─────┘              ▼
                 ▼               Mock Gateway
            PostgreSQL            (asifmahmoud414/
                                  mock-gateway:latest)
```

The frontend (`backend/src/main/resources/static/`) is served by Spring Boot at `/`.

## Tech stack

- Java 21 + Spring Boot 3.3
- PostgreSQL 16 + Flyway migrations
- JPA / Hibernate (no optimistic locking on hot paths — pessimistic `FOR UPDATE`)
- Vanilla HTML/CSS/JS frontend
- Docker Compose + GitHub Actions
- k6 for load testing

## Local setup

```bash
git clone https://github.com/TISamin/CineCli.git
cd CineCli
docker compose up --build
```

When `Started CinemaSeatApplication` appears, verify:

```bash
curl http://localhost:8080/health
# {"status":"UP"}
```

The static frontend lives in two places: bundled inside the Spring Boot jar
(`backend/src/main/resources/static/`) for local + Render deploys, and extracted
to `frontend/` for Vercel deploys. Both are kept in sync.

## API documentation

### Browse

```bash
curl http://localhost:8080/api/movies
curl http://localhost:8080/api/movies/1/shows
curl http://localhost:8080/api/shows/1/seats
```

### Hold a seat

```bash
curl -X POST http://localhost:8080/api/shows/1/seats/A1/hold \
     -H 'Content-Type: application/json' \
     -d '{"phone":"01700000000"}'
```

Response (200):

```json
{
  "bookingRef": "BK-2026-08-09-AB12CD",
  "holdToken": "...",
  "expiresAt": "2026-08-09T12:01:30Z",
  "amount": 450,
  "ref": "BK-2026-08-09-AB12CD"
}
```

409 on contention (seat held/booked by another user).

### Pay

```bash
curl -X POST http://localhost:8080/api/bookings/BK-2026-08-09-AB12CD/pay \
     -H 'Content-Type: application/json' \
     -d '{"holdToken":"...","phone":"01700000000"}'
```

Returns immediately. Final state arrives via the gateway callback.

### Refund (addendum B6)

```bash
curl -X POST http://localhost:8080/api/bookings/BK-2026-08-09-AB12CD/refund
```

The gateway replies 202 then sends a `REFUNDED` callback. Final state: `booking=REFUNDED`, seat freed.

### Health

```bash
curl http://localhost:8080/health          # liveness — never touches DB or gateway
curl http://localhost:8080/health/ready    # readiness — DB probe (500ms)
```

## Gateway

The app integrates with the official mock gateway: `asifmahmoud414/mock-gateway:latest`.

| Concern | How the app handles it |
|---------|------------------------|
| Callback URL | Compose default: `http://api:8080/api/payments/callback`. Must be reachable from inside the gateway container. |
| HMAC signature | Verified on raw body via `RawBodyFilter` + `SignatureVerifier`. Constant-time compare with `GATEWAY_SECRET=z2p-2026-secret`. |
| Duplicate callbacks | Deduplicated by `UNIQUE(event_id)` on `payment_event`. Returns 200 on duplicates. |
| Async state | `/charge` returns immediately; final state arrives at callback. `/charge` 5xx keeps payment PENDING. |
| Idempotency | `Idempotency-Key` header sent on every `/charge`. Same key -> same `payment_id`. |
| Force modes | All five tested (`success`, `fail`, `duplicate`, `timeout`, `race`). |
| Refund | `POST /api/bookings/{ref}/refund` -> gateway `/refund` -> REFUNDED callback. |

### Debug endpoints (gateway)

```bash
curl http://localhost:9000/debug/deliveries?booking_ref=BK-...
curl http://localhost:9000/debug/payments
curl -X POST http://localhost:9000/debug/reset    # wipes gateway state
```

## Testing

### Maven

```bash
cd backend && mvn test
```

The keystone test (`ScenarioAConcurrencyTest`) uses Testcontainers to spin up a real Postgres and runs 100 concurrent hold requests against the same seat. Expected: **1 success, 99 conflicts (HTTP 409), 0 oversell**.

### k6 load tests

```bash
# Scenario A: 100 concurrent same-seat holds
k6 run -e BASE_URL=http://localhost:8080 tests/load/scenario-a.js

# Scenario B: hold -> wait TTL -> re-hold
k6 run -e BASE_URL=http://localhost:8080 -e HOLD_TTL_SECONDS=10 -e WAIT_SECONDS=15 tests/load/scenario-b.js
```

## Deployment

See **[DEPLOY.md](DEPLOY.md)** for one-click deploy to Render + Vercel.

The compose file is also production-ready: point `GATEWAY_CALLBACK_URL` and
`GATEWAY_SECRET` at the production values via environment overrides.

## Known limitations

- **No group bookings.** The `/hold` endpoint accepts one seat at a time.
- **No refund endpoint result wait.** `/api/bookings/{ref}/refund` returns 202; final state arrives via callback.
- **OTP verification may need polling.** Gateway reference notes 10% silent OTP loss.
- **First-call latency on the gateway.** The gateway deliberately delays callbacks 2-15 seconds.

## Decisions

See `DECISIONS.md` for the 18 design choices (D1-D18) including the row-locking
strategy, idempotency layers, and async payment model.

## External resources

- Java 21 (Temurin)
- Spring Boot 3.3
- PostgreSQL 16
- Flyway
- Docker
- GitHub Actions
- k6
- Mock payment gateway: [`asifmahmoud414/mock-gateway`](https://hub.docker.com/r/asifmahmoud414/mock-gateway)

Competition: **IEEE Computer Society CUET SB** + **Poridhi.io**.