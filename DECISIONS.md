# DECISIONS

Design decisions documented during the build of CinemaSeat. D1-D3 are required by the
competition; D4-D18 are addenda from the critical review and the gateway reference.

---

## D1 — Modular monolith vs microservices

**Choice:** Modular monolith.

**Reason:** Limited build window, simpler transactions, simpler deployment, fewer
moving parts. Still provides clear package boundaries (`booking`, `payment`, `seat`,
`movie`, `show`, `screen`, `theatre`, `gateway`, `otp`, `common`, `config`).

**Trade-off:** Less independent scaling. Modules share one process.

---

## D2 — How to prevent double booking

**Options considered:**
- A. Application-level synchronized lock
- B. Redis distributed lock
- C. Database row lock
- D. Optimistic locking

**Choice:** C — Database transaction + `SELECT ... FOR UPDATE` (`@Lock(PESSIMISTIC_WRITE)`).

**Reason:** Booking inventory already lives in PostgreSQL. Correctness is enforced
close to the data, no extra infrastructure, easy to explain.

**Trade-off:** Concurrent requests for the same seat serialize. Acceptable: the
business case is exactly that only one buyer wins.

---

## D3 — Payment processing model

**Choice:** Asynchronous callback.

**Reason:** Gateway callbacks are delayed (2-15s), can be duplicated (8%), can timeout
(2%), can arrive before `/charge` returns (race mode). The HTTP request must not wait
for the final state.

**Trade-off:** More complicated state management; requires idempotency layers.

---

## D4 — ShowSeat `version` column

**Choice:** Dropped.

**Reason:** The base plan included `version` (suggesting Hibernate `@Version` optimistic
locking), but D2 commits to pessimistic `FOR UPDATE`. Mixing both produces
`OptimisticLockException` storms under contention. The hot path is intentionally
serialized; no retries needed.

---

## D5 — Re-hold by same user

**Choice:** Idempotent — return the existing token, extend TTL, HTTP 200.

**Reason:** Browser refresh is common. Re-holding should not be punished with a 409.

---

## D6 — Late callback after seat legitimately re-held

**Choice:** Mark `Payment=SUCCEEDED`, `Booking=EXPIRED`. Do not steal the seat from the
new legitimate owner.

**Reason:** The merchant-side money record stays accurate; the customer's lost seat
is a refund-eligible event we acknowledge but handle out-of-band.

---

## D7 — Callback exception policy

**Choice:** Catch all exceptions inside the `/api/payments/callback` handler. Log with
`event_id` and `booking_ref`, return HTTP 200.

**Reason:** The gateway retries up to 8 times with exponential backoff on non-2xx.
A 500 exception must not cause retry storms; the `event_id` dedup layer makes
re-processing safe.

---

## D8 — Cleanup job implementation

**Choice:** Scheduled job every 10s. Uses `SELECT ... FOR UPDATE SKIP LOCKED LIMIT 100`
filtering `status='HELD' AND hold_expires_at < now()`.

**Reason:** The lazy expiration check on `hold()` is the correctness guarantee. The
cleanup job is memory hygiene only — releases the row so the seat-map endpoint
reports the right status sooner.

---

## D9 — Refund path

**Choice:** Implemented. `POST /api/bookings/{ref}/refund` -> gateway `/refund`
-> `REFUNDED` callback -> `Booking=REFUNDED`, seat freed.

**Reason:** The gateway provides a built-in `/refund` endpoint that returns 202 and
sends a `REFUNDED` callback on the same idempotent path. Implementation cost is
small; the late-callback fallback in D6 needs it.

---

## D10 — Health endpoints

**Choice:** Two endpoints.
- `GET /health` — liveness, never touches DB or gateway. Always 200 if JVM is running.
- `GET /health/ready` — readiness, 500ms DB pool probe.

**Reason:** Kubernetes-style split. Preserves the gateway-isolation rule from the
base plan.

---

## D11 — Seed strategy

**Choice:** Flyway `V2__seed.sql` only. No JPA startup inserts.

**Reason:** Flyway is deterministic and runs before tests. JPA startup inserts are
fragile and break test isolation.

---

## D12 — Connection pool sizing

**Choice:** HikariCP `maximum-pool-size=30`, `connection-timeout=3000`.

**Reason:** Spring Boot default of 10 will queue the 100 concurrent Scenario A
requests and produce false 5xx.

---

## D13 — Time / zone

**Choice:** All timestamps `TIMESTAMPTZ`. API responses use ISO-8601 UTC with `Z`.
Jackson and Hibernate set to UTC.

**Reason:** DST boundaries and time-zone confusion are common bugs in booking systems.

---

## D14 — Callback URL

**Choice:** Compose default: `http://api:8080/api/payments/callback`. Production
override: `https://<poridhi-domain>/api/payments/callback`. Driven by env var
`GATEWAY_CALLBACK_URL`.

**Reason:** Per the gateway reference, the callback URL MUST be reachable from inside
the gateway container. Compose service name (`api`) is the correct choice in dev.

---

## D15 — Service name in compose

**Choice:** `api` (renamed from `app` in the base plan).

**Reason:** The gateway reference's example URLs assume the service is named `api`.
Matching avoids confusion.

---

## D16 — `Idempotency-Key` on `/charge`

**Choice:** Generated per `(booking_ref, attempt_id)` and sent on every `/charge`.

**Reason:** Per the gateway reference, the same key returns the same `payment_id`
without a second charge. Adds an upstream idempotency layer to the downstream
UNIQUE constraints.

---

## D17 — HMAC signature verification

**Choice:** Required (not bonus). HMAC-SHA256 over the **raw body** using
`GATEWAY_SECRET=z2p-2026-secret`. Header `X-Signature`. Constant-time compare.

**Reason:** Anyone on the internet can POST to the webhook path. Verification
catches attackers. Raw-body capture via `CachedBodyHttpServletRequest` (re-serialized
JSON breaks the signature).

---

## D18 — Retry budget

**Choice:** Always return HTTP 200 to the gateway (D7). The gateway retries up to
8 times with exponential backoff; 5s timeout per attempt.

**Reason:** Returning 5xx causes retry storms that the `event_id` dedup layer would
make safe, but explicit 200 is cleaner and matches the gateway reference's
three callback rules verbatim.