# CinemaSeat --- Complete Implementation Plan

## Zero to Production --- Phase 2

> **Goal:** Build a scalable, reliable cinema ticket-booking system that
> remains correct under concurrent demand and **never double-books a
> seat**.

This plan is based on the official CinemaSeat problem statement and the
Zero to Production rulebook. The implementation is intentionally
optimized for the competition's scoring model, limited build window,
required gateway behavior, judging hooks, and mandatory scenarios.

------------------------------------------------------------------------

# 1. Competition Strategy

## Primary objective

Do not try to build a complete commercial cinema platform.

The winning priority is:

1.  Correct seat ownership under concurrency
2.  Correct booking/payment state transitions
3.  Idempotent payment callback handling
4.  Automatic hold expiration
5.  Meaningful unit/concurrency tests
6.  Docker + Compose
7.  CI
8.  Public deployment
9.  Clear README + DECISIONS.md
10. Minimal but functional UI

The problem explicitly says a polished UI is optional and that the core
path is:

**browse → seat select → hold → pay → confirm**

The judges will test the system under deterministic and forced gateway
failures/races, so correctness must be designed rather than demonstrated
only in a happy-path demo.

------------------------------------------------------------------------

# 2. Recommended Technology Stack

## Backend

-   Java 21
-   Spring Boot
-   Spring Web
-   Spring Data JPA / Hibernate
-   Bean Validation
-   PostgreSQL
-   Flyway for database migrations
-   JUnit 5
-   Mockito
-   Testcontainers where practical

## Frontend

Keep it intentionally simple:

-   HTML
-   CSS
-   Vanilla JavaScript

A full React/Vue application is unnecessary for this competition unless
the team already has a prepared template.

## Infrastructure

-   Docker
-   Docker Compose
-   GitHub Actions
-   Poridhi VM + Load Balancer for deployment

The problem states that Poridhi VM/Cloud and AWS are both acceptable.
The Poridhi VM path is the recommended competition choice because it has
fewer moving parts and is faster to ship.

## Load testing

-   k6

Run the load generator from a laptop/host against the deployed
application, not from the same machine as the application.

------------------------------------------------------------------------

# 3. High-Level Architecture

Use a **modular monolith**, not unnecessary microservices.

The problem explicitly says service splitting is optional and that a
simple architecture you can explain is better than a complicated
architecture you cannot defend.

Recommended architecture:

``` text
                         Browser
                            |
                            v
                    +---------------+
                    |  Spring Boot  |
                    |   REST API    |
                    +-------+-------+
                            |
              +-------------+-------------+
              |             |             |
              v             v             v
        Movie/Show      Booking/Seat    Payment
          Module          Module         Module
              |             |             |
              +-------------+-------------+
                            |
                            v
                       PostgreSQL
                            ^
                            |
                    +-------+-------+
                    | Mock Gateway  |
                    | payment + OTP |
                    +---------------+
```

For deployment:

``` text
Internet
   |
   v
Poridhi Load Balancer
   |
   v
Spring Boot Application
   |
   +---- PostgreSQL
   |
   +---- Mock Gateway
```

The frontend can be served directly by Spring Boot to keep deployment
simple.

------------------------------------------------------------------------

# 4. Why a Modular Monolith?

## Advantages

-   One application to build and deploy
-   One database
-   No inter-service networking complexity
-   Easier transactions
-   Easier concurrency control
-   Easier local development
-   Easier Docker Compose
-   Easier debugging during an eight-hour competition
-   Still provides clear module boundaries for the architecture score

## Modules

Create packages such as:

``` text
com.cinemaseat
├── movie
├── theatre
├── show
├── seat
├── booking
├── payment
├── gateway
├── common
└── config
```

Do not put everything in one controller/service.

------------------------------------------------------------------------

# 5. Core Domain Model

Keep the schema small.

## Movie

``` text
Movie
-----
id
title
description
duration_minutes
language
rating
```

## Theatre

``` text
Theatre
--------
id
name
location
```

## Screen

``` text
Screen
------
id
theatre_id
name
```

## Seat

A physical seat belonging to a screen.

``` text
Seat
----
id
screen_id
row_label
seat_number
seat_code
```

Example:

``` text
F12
```

## Show

A particular movie playing on a particular screen.

``` text
Show
----
id
movie_id
screen_id
start_time
end_time
```

## ShowSeat

This is the critical table.

Each show gets its own seat inventory.

``` text
ShowSeat
--------
id
show_id
seat_id
price
status
hold_token
hold_expires_at
booking_id
version
```

Possible status values:

``` text
AVAILABLE
HELD
BOOKED
```

The important idea is:

> A physical seat is not directly booked globally. A `ShowSeat`
> represents that seat for one particular show.

So F12 for Show 101 and F12 for Show 102 are separate inventory records.

------------------------------------------------------------------------

# 6. Booking Model

``` text
Booking
-------
id
booking_ref
show_id
user_phone
total_amount
status
created_at
updated_at
```

Booking status:

``` text
PENDING_PAYMENT
CONFIRMED
PAYMENT_FAILED
EXPIRED
```

Potentially:

``` text
CANCELLED
REFUNDED
```

if time permits.

A booking should have a unique public reference such as:

``` text
BK-20260808-AB12CD
```

------------------------------------------------------------------------

# 7. Payment Model

``` text
Payment
-------
id
payment_id
booking_id
amount
currency
status
created_at
updated_at
```

Payment status:

``` text
PENDING
SUCCEEDED
FAILED
REFUNDED
```

Important database constraints:

``` text
UNIQUE(payment_id)
UNIQUE(booking_id)
```

This is important for idempotency.

------------------------------------------------------------------------

# 8. Gateway Event Model

The callback can arrive twice.

Store processed gateway events.

``` text
PaymentEvent
------------
id
event_id
payment_id
booking_ref
status
amount
received_at
```

Constraint:

``` text
UNIQUE(event_id)
```

This gives us a durable idempotency boundary.

------------------------------------------------------------------------

# 9. The Most Important Invariant

## Never double-book a ShowSeat

The system must guarantee:

``` text
For every ShowSeat:
    status = BOOKED
    → at most one booking owns it
```

Do not rely only on:

``` java
if (seat.status == AVAILABLE) {
    seat.status = HELD;
}
```

Two concurrent requests can both read `AVAILABLE`.

The database transaction must provide the concurrency guarantee.

------------------------------------------------------------------------

# 10. Recommended Seat-Hold Algorithm

Use a database transaction with row locking.

Conceptually:

``` text
BEGIN TRANSACTION

SELECT show_seat
WHERE show_id = ?
AND seat_id = ?
FOR UPDATE

if seat is BOOKED:
    reject

if seat is HELD and hold has not expired:
    reject

if seat is HELD and hold has expired:
    treat as AVAILABLE

create/update booking
set ShowSeat = HELD
set hold_token
set hold_expires_at

COMMIT
```

The important operation is:

``` sql
SELECT ...
FOR UPDATE
```

This makes concurrent requests for the same `ShowSeat` serialize.

Request A gets the row lock first.

Request B waits.

After A commits:

``` text
A → HELD
B → reads HELD → rejects
```

Therefore:

``` text
100 requests
↓
1 success
99 rejection
0 oversell
```

This directly addresses Scenario A.

------------------------------------------------------------------------

# 11. Hold Token

Every successful hold receives a unique token.

Example:

``` text
hold_token = UUID
```

The frontend must use this token when continuing payment.

Example response:

``` json
{
  "bookingRef": "BK-123",
  "holdToken": "8b8c...",
  "expiresAt": "2026-08-08T12:01:30Z",
  "status": "HELD"
}
```

The token prevents another user from paying for somebody else's hold.

------------------------------------------------------------------------

# 12. Hold Expiration

The environment variable must be used:

``` text
HOLD_TTL_SECONDS
```

Example:

``` env
HOLD_TTL_SECONDS=120
```

Do not hardcode the value in Java.

## Expiration strategy

Use a scheduled cleanup job:

``` text
Every few seconds
      |
      v
Find HELD seats where hold_expires_at < now
      |
      v
Release them
      |
      v
AVAILABLE
```

However, correctness should not depend solely on the scheduler.

When attempting to hold a seat:

``` text
if status == HELD
and hold_expires_at <= now:
    treat it as AVAILABLE
```

This means an expired seat can be immediately reused even if the
background cleanup has not run yet.

------------------------------------------------------------------------

# 13. Booking Flow

## Step 1 --- Browse

``` http
GET /api/movies
GET /api/movies/{movieId}/shows
GET /api/shows/{showId}/seats
```

The seat map should return:

``` json
{
  "showId": 1,
  "seats": [
    {
      "seatCode": "F12",
      "price": 450,
      "status": "AVAILABLE"
    }
  ]
}
```

------------------------------------------------------------------------

# 14. Hold Endpoint

Recommended:

``` http
POST /api/shows/{showId}/seats/{seatId}/hold
```

Request:

``` json
{
  "phone": "017XXXXXXXX"
}
```

Response:

``` json
{
  "bookingRef": "BK-123",
  "holdToken": "uuid",
  "expiresAt": "...",
  "amount": 450
}
```

Possible errors:

``` text
409 Conflict
```

when another user already holds/books the seat.

Use `409` rather than `500` for expected contention.

------------------------------------------------------------------------

# 15. Payment Flow

Recommended endpoint:

``` http
POST /api/bookings/{bookingRef}/pay
```

Request:

``` json
{
  "holdToken": "uuid",
  "phone": "017XXXXXXXX"
}
```

The payment handler should:

1.  Validate booking
2.  Validate hold token
3.  Validate that the hold has not expired
4.  Create a `Payment` with `PENDING`
5.  Call gateway `/charge`
6.  Return quickly
7.  Wait for gateway callback asynchronously

Do NOT wait for the final payment result.

The gateway itself may delay callbacks by 2--15 seconds.

------------------------------------------------------------------------

# 16. Gateway Charge

The gateway expects:

``` http
POST /charge
```

with:

``` json
{
  "amount": 450,
  "currency": "BDT",
  "booking_ref": "BK-123",
  "callback_url": "https://your-domain/api/payments/callback"
}
```

During development, use:

``` text
X-Mock-Mode: deterministic
```

Then test every force mode before submission:

``` text
X-Mock-Force: success
X-Mock-Force: fail
X-Mock-Force: duplicate
X-Mock-Force: timeout
X-Mock-Force: race
```

Do not leave deterministic mode enabled for the final demonstration.

------------------------------------------------------------------------

# 17. Payment Callback

Endpoint:

``` http
POST /api/payments/callback
```

Example:

``` json
{
  "event_id": "evt_001",
  "payment_id": "pay_xyz",
  "booking_ref": "bk_001",
  "status": "SUCCEEDED",
  "amount": 450
}
```

## Callback algorithm

``` text
Receive callback
      |
      v
Start transaction
      |
      v
Check event_id
      |
      +---- already processed → return 200
      |
      v
Insert PaymentEvent
      |
      v
Lock Payment row
      |
      v
Lock Booking / ShowSeat rows as needed
      |
      v
Check current state
      |
      +---- already CONFIRMED → no-op
      |
      v
If SUCCEEDED:
      confirm booking
      mark seat BOOKED
      mark payment SUCCEEDED
      |
      v
If FAILED:
      mark payment FAILED
      release held seat
      mark booking PAYMENT_FAILED
      |
      v
Commit
      |
      v
Return HTTP 200
```

## Critical rule

Even duplicate callbacks must return:

``` http
200 OK
```

Otherwise the gateway may keep retrying.

------------------------------------------------------------------------

# 18. Idempotency Design

There should be multiple protections.

## Protection 1

``` text
UNIQUE(event_id)
```

prevents the same gateway event from being processed twice.

## Protection 2

``` text
UNIQUE(payment_id)
```

prevents duplicate payment records.

## Protection 3

Booking state transitions are conditional.

For example:

``` text
PENDING_PAYMENT → CONFIRMED
```

is valid.

But:

``` text
CONFIRMED → CONFIRMED
```

is a no-op.

## Protection 4

Seat ownership is protected by the database row lock.

Together these protect against:

``` text
duplicate callback
callback retry
callback race
double confirmation
double revenue counting
```

------------------------------------------------------------------------

# 19. Race Condition: Callback Before Charge Returns

The gateway provides:

``` text
X-Mock-Force: race
```

where the callback may arrive before `/charge` returns.

Therefore the system must not assume:

``` text
charge request returns
    ↓
then callback happens
```

Instead:

``` text
Payment is created as PENDING
        |
        +-------------------+
        |                   |
        v                   v
   charge request       callback
        |                   |
        +--------+----------+
                 |
                 v
          database state
```

If the callback wins the race:

``` text
Payment → SUCCEEDED
Booking → CONFIRMED
Seat → BOOKED
```

Then when `/charge` finally returns, the application must not overwrite
that successful state incorrectly.

------------------------------------------------------------------------

# 20. Failed Payment

If callback says:

``` text
FAILED
```

then:

``` text
Payment = FAILED
Booking = PAYMENT_FAILED
ShowSeat = AVAILABLE
```

provided the booking has not already been confirmed.

The seat should become available again for another buyer.

------------------------------------------------------------------------

# 21. Payment Timeout / Charge 500

If `/charge` returns a 500 or times out:

Do not immediately assume:

``` text
PAYMENT_FAILED
```

because the gateway may have accepted the payment and the callback may
arrive later.

Keep the payment in:

``` text
PENDING
```

and allow the callback to determine the final state.

This is particularly important because the gateway deliberately
introduces charge failures/timeouts.

------------------------------------------------------------------------

# 22. OTP

The problem requires integration with the provided OTP gateway.

Implement:

``` http
POST /api/otp/send
POST /api/otp/verify
```

Use the gateway's:

``` text
/otp/send
/otp/verify
```

Do not make OTP delivery a dependency for the core booking database
correctness.

Because OTP may be delayed or never delivered, the system should handle
that failure cleanly rather than crashing the booking service.

------------------------------------------------------------------------

# 23. API List

Keep the API small.

## Movies

``` http
GET /api/movies
```

## Shows

``` http
GET /api/movies/{movieId}/shows
GET /api/shows/{showId}
```

## Seats

``` http
GET /api/shows/{showId}/seats
POST /api/shows/{showId}/seats/{seatId}/hold
```

## Booking

``` http
GET /api/bookings/{bookingRef}
POST /api/bookings/{bookingRef}/pay
```

## Payment

``` http
POST /api/payments/callback
```

## OTP

``` http
POST /api/otp/send
POST /api/otp/verify
```

## Health

``` http
GET /health
```

------------------------------------------------------------------------

# 24. Health Endpoint

This endpoint must be extremely lightweight.

``` http
GET /health
```

Return:

``` json
{
  "status": "UP"
}
```

with HTTP 200.

Do NOT make `/health` depend on the payment gateway.

The judge will deliberately stop the gateway and verify that:

``` text
GET /health → 200
```

still works.

------------------------------------------------------------------------

# 25. Database Initialization

Pre-populate:

-   several movies
-   multiple theatres
-   screens
-   realistic seat layouts
-   several showtimes
-   different ticket prices

Example:

``` text
Theatre A
 ├── Screen 1
 │    ├── A1 ... A12
 │    ├── B1 ... B12
 │    └── ...
 │
 └── Screen 2
```

For each show:

``` text
ShowSeat rows = all seats belonging to its screen
```

This makes the seat map fast and simple.

------------------------------------------------------------------------

# 26. Frontend

Keep the frontend minimal.

## Page 1 --- Movie list

Show:

``` text
Movie
Description
Showtimes
```

## Page 2 --- Seat map

Display:

``` text
AVAILABLE
HELD
BOOKED
```

Example:

``` text
[A1] [A2] [A3] [A4] [A5]

[B1] [B2] [B3] [B4] [B5]

[C1] [C2] [C3] [C4] [C5]
```

## Page 3 --- Checkout

Show:

``` text
Movie
Showtime
Seat
Price
Phone
```

Then:

``` text
Hold
Pay
```

## Page 4 --- Confirmation

Show:

``` text
Booking confirmed
Booking reference
Movie
Show
Seat
Amount
```

Do not spend hours on animations or visual polish.

------------------------------------------------------------------------

# 27. Repository Structure

Recommended:

``` text
cinemaseat/
│
├── backend/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/cinemaseat/
│   │   │   │   ├── movie/
│   │   │   │   ├── theatre/
│   │   │   │   ├── show/
│   │   │   │   ├── seat/
│   │   │   │   ├── booking/
│   │   │   │   ├── payment/
│   │   │   │   ├── gateway/
│   │   │   │   ├── common/
│   │   │   │   └── config/
│   │   │   └── resources/
│   │   │       ├── application.yml
│   │   │       └── db/migration/
│   │   │
│   │   └── test/
│   │
│   └── Dockerfile
│
├── frontend/
│   ├── index.html
│   ├── style.css
│   └── app.js
│
├── tests/
│   └── load/
│       └── scenario-a.js
│
├── .github/
│   └── workflows/
│       └── ci.yml
│
├── docker-compose.yml
├── .env.example
├── README.md
├── DECISIONS.md
└── .gitignore
```

------------------------------------------------------------------------

# 28. Docker Compose

Services:

``` yaml
services:
  app:
    build: ./backend
    depends_on:
      postgres:
        condition: service_healthy
      gateway:
        condition: service_started

  postgres:
    image: postgres:16

  gateway:
    image: asifmahmoud414/mock-gateway:latest
    ports:
      - "9000:9000"
```

Use environment variables.

Example:

``` env
POSTGRES_DB=cinemaseat
POSTGRES_USER=cinemaseat
POSTGRES_PASSWORD=...
HOLD_TTL_SECONDS=120
GATEWAY_URL=http://gateway:9000
```

Never commit real secrets.

------------------------------------------------------------------------

# 29. Docker Health Checks

PostgreSQL should have a health check.

Example concept:

``` text
pg_isready
```

The application should start after PostgreSQL is ready.

The gateway does not need to be considered healthy for `/health` to
work.

------------------------------------------------------------------------

# 30. CI Pipeline

Create:

``` text
.github/workflows/ci.yml
```

Pipeline:

``` text
Push / Pull Request
        |
        v
Checkout
        |
        v
Setup Java
        |
        v
Run unit tests
        |
        v
Build application
        |
        v
Build Docker image
        |
        v
Success / Failure
```

Required behavior:

-   CI on pushes
-   CI on pull requests
-   tests must pass
-   Docker image must build

If time allows:

``` text
default branch push
       ↓
build
       ↓
test
       ↓
deploy
```

------------------------------------------------------------------------

# 31. Testing Strategy

Testing is worth 15 marks, so do not leave it until the end.

## Unit tests

Prioritize:

### Seat hold logic

Test:

``` text
AVAILABLE → HELD
HELD → rejected
BOOKED → rejected
expired HELD → HELD again
```

### Payment state machine

Test:

``` text
PENDING → SUCCEEDED
PENDING → FAILED
SUCCEEDED → SUCCEEDED = no-op
FAILED → FAILED = no-op
```

### Duplicate callback

Same `event_id` twice:

``` text
first → processed
second → no-op
```

### Race callback

Callback before `/charge` response must still result in the correct
final state.

------------------------------------------------------------------------

# 32. Concurrency Test

This is one of the most important tests.

Create 100 concurrent hold requests for:

``` text
same show
same seat
```

Expected:

``` text
successful = 1
rejected = 99
oversell = 0
```

Do not test 100 users across 100 seats. The competition explicitly says
that does not prove anything.

------------------------------------------------------------------------

# 33. Scenario A --- Required

Prepare a repeatable k6 test.

Conceptually:

``` javascript
100 virtual requests
→ same hold endpoint
→ same show
→ same seat
```

Collect:

``` text
requests sent
successful holds
rejections
oversell count
latency
```

Expected:

``` text
100 requests
1 success
99 rejected
0 oversell
```

Record the output for the README/demo.

------------------------------------------------------------------------

# 34. Scenario B --- Required

Use a short:

``` env
HOLD_TTL_SECONDS=10
```

Then:

``` text
1. Hold seat
2. Do not pay
3. Wait > 10 seconds
4. Fetch seat map
5. Confirm AVAILABLE
6. Different user holds it
7. Confirm success
```

Document the observed timeline.

------------------------------------------------------------------------

# 35. Scenario C --- Bonus

Only attempt after A, B, Docker, CI and deployment are stable.

Ramp traffic against:

``` text
GET /api/shows/{showId}/seats
POST /api/shows/{showId}/seats/{seatId}/hold
```

Find:

``` text
p95 latency turning upward
error rate beginning
system bottleneck
```

Explain whether the bottleneck is:

-   database contention
-   connection pool
-   CPU
-   memory
-   application thread pool
-   blocked operations

The explanation matters more than raw throughput.

------------------------------------------------------------------------

# 36. Fault Isolation Bonus

If time allows:

Stop the gateway container.

Verify:

``` text
GET /api/movies → works
GET /api/shows/.../seats → works
POST hold → works
GET /health → 200
```

Then restart the gateway.

Pending payments should recover through callbacks where applicable.

------------------------------------------------------------------------

# 37. Observability Bonus

If the core system is already stable, add:

-   structured JSON logs
-   request ID
-   booking reference in logs
-   payment ID in logs
-   metrics endpoint

Useful metrics:

``` text
booking_attempts
booking_successes
booking_rejections
payment_successes
payment_failures
payment_pending
callback_duplicates
hold_expirations
```

Do not build a huge monitoring stack unless the required system is
already finished.

------------------------------------------------------------------------

# 38. Security Bonus

Only after core functionality is stable.

Potential additions:

-   input validation
-   rate limiting
-   basic authentication
-   authorization
-   callback signature verification if supported by the gateway
-   safe error responses
-   no secrets in Git

Input validation should be implemented even without the bonus because it
contributes to code quality.

------------------------------------------------------------------------

# 39. Logging

Use structured logs.

Every important request should include:

``` text
request_id
booking_ref
show_id
seat_id
payment_id
event_id
```

Example:

``` text
booking_ref=BK-123
seat=F12
event=SEAT_HELD
```

Avoid logging sensitive information unnecessarily.

------------------------------------------------------------------------

# 40. Error Handling

Use consistent HTTP status codes.

``` text
200 OK
201 Created
400 Bad Request
404 Not Found
409 Conflict
500 Internal Server Error
```

Examples:

``` text
Seat already held → 409
Seat already booked → 409
Invalid hold token → 400/409
Movie not found → 404
Unexpected database error → 500
```

Never silently swallow exceptions.

------------------------------------------------------------------------

# 41. Transaction Boundaries

The most important transactions:

## Hold

``` text
BEGIN
lock ShowSeat
validate state
create booking
update ShowSeat
COMMIT
```

## Callback

``` text
BEGIN
deduplicate event
lock payment/booking/seat state as necessary
apply valid state transition
COMMIT
```

Do not make the HTTP request to the gateway part of a long database
transaction.

------------------------------------------------------------------------

# 42. State Machine

Document this clearly because judges are likely to ask about it.

## Booking

``` text
             payment success
PENDING_PAYMENT ----------------> CONFIRMED
      |
      | payment failure
      v
PAYMENT_FAILED

PENDING_PAYMENT
      |
      | hold expires
      v
EXPIRED
```

## Seat

``` text
AVAILABLE
    |
    | successful hold
    v
HELD
 |   |
 |   | payment success
 |   v
 |  BOOKED
 |
 | payment failure / expiration
 v
AVAILABLE
```

This is one of the most important diagrams for the presentation.

------------------------------------------------------------------------

# 43. Handling Hold Expiration + Payment Race

There is an important edge case:

``` text
Hold expires
       |
       +------ User payment callback arrives
```

The system must define a consistent rule.

Recommended rule:

A successful payment may confirm the booking only if the booking/hold is
still valid and the seat is still associated with that booking.

If the hold has already been released and another user has acquired the
seat, do not steal the seat back.

If payment succeeds after the seat has been legitimately released, the
system should not overwrite the new owner's booking.

Depending on the gateway semantics, the safest fallback is to mark the
payment as succeeded but trigger a refund path if the booking can no
longer be honored.

Document this decision in `DECISIONS.md`.

------------------------------------------------------------------------

# 44. DECISIONS.md

The problem explicitly requires three genuine design decisions.

Prepare these three topics:

## Decision 1 --- Modular monolith vs microservices

Options:

``` text
A. Microservices
B. Modular monolith
```

Choose:

``` text
Modular monolith
```

Reason:

-   limited time
-   simpler transactions
-   simpler deployment
-   easier concurrency control
-   fewer moving parts
-   still provides clear module boundaries

Trade-off:

-   less independent scaling
-   modules share one process

------------------------------------------------------------------------

## Decision 2 --- How to prevent double booking

Options:

``` text
A. Application-level synchronized lock
B. Redis distributed lock
C. Database row lock
D. Optimistic locking
```

Recommended:

``` text
Database transaction + row locking
```

Reason:

-   booking inventory already lives in PostgreSQL
-   correctness is enforced close to the data
-   no additional infrastructure
-   easy to explain
-   suitable for the competition's single deployed application

Trade-off:

-   concurrent requests for the same seat serialize

------------------------------------------------------------------------

## Decision 3 --- Payment processing model

Options:

``` text
A. Synchronous payment
B. Callback/event-driven payment
```

Choose:

``` text
Asynchronous callback
```

Reason:

-   gateway callbacks are delayed
-   gateway can timeout
-   callbacks can be duplicated
-   callback can arrive before charge response
-   HTTP request should not wait for final payment

Trade-off:

-   more complicated state management
-   requires idempotency

------------------------------------------------------------------------

# 45. README Requirements

README must contain:

## 1. Project overview

Explain CinemaSeat in 2--3 paragraphs.

## 2. Architecture

Include architecture diagram.

## 3. Technology stack

List:

``` text
Java
Spring Boot
PostgreSQL
Docker
GitHub Actions
Poridhi VM
Mock Gateway
```

## 4. Local setup

Exact:

``` bash
git clone ...
cd cinemaseat
cp .env.example .env
docker compose up --build
```

## 5. API documentation

Include exact request examples for:

### Hold seat

``` bash
curl ...
```

### Seat map

``` bash
curl ...
```

The problem explicitly requires these exact requests.

## 6. Gateway

Document:

``` text
gateway URL
charge flow
callback flow
test headers
```

## 7. Testing

Explain:

``` bash
./mvnw test
```

and k6 Scenario A/B.

## 8. Deployment

Include deployed URL.

## 9. Known limitations

Be honest.

## 10. External resources

List every external library/API/service as required by the competition
rules.

------------------------------------------------------------------------

# 46. Team Division

Three people should work in parallel.

## Person 1 --- Backend / Concurrency Owner

Own:

``` text
database
entities
repositories
transactions
seat locking
hold logic
booking state machine
```

Highest priority.

------------------------------------------------------------------------

## Person 2 --- Payment / Integration Owner

Own:

``` text
gateway integration
payment state
callback
idempotency
OTP
payment tests
gateway failure scenarios
```

Coordinate closely with Person 1 because payment changes booking/seat
state.

------------------------------------------------------------------------

## Person 3 --- Frontend + DevOps Owner

Own:

``` text
frontend
Docker Compose
GitHub Actions
deployment
README
architecture diagram
k6 scripts
```

This person should start Docker/CI/deployment early rather than waiting
for backend completion.

------------------------------------------------------------------------

# 47. Shared Git Workflow

Immediately after problem reveal:

``` text
main
```

Create branches:

``` text
feature/booking
feature/payment
feature/frontend-devops
```

Commit frequently.

Example:

``` text
feat: add movie and show entities
feat: implement show seat inventory
feat: implement transactional seat hold
feat: integrate mock payment gateway
feat: add idempotent callback handling
test: add concurrent seat hold test
chore: add docker compose
ci: add github actions pipeline
docs: add architecture and setup guide
```

Do not make meaningless commits.

------------------------------------------------------------------------

# 48. Build Timeline

## 09:00--09:30

Problem reveal.

Immediately:

``` text
09:00–09:10
Read requirements

09:10–09:25
Architecture + database design

09:25–09:30
Create repository + branches
```

Do not start randomly coding.

------------------------------------------------------------------------

# 49. 09:30--10:30 --- Design + Skeleton

Complete:

-   architecture
-   database model
-   API list
-   state machine
-   team responsibilities
-   repository
-   Spring Boot skeleton
-   Docker Compose skeleton

Target:

``` text
docker compose up
```

should already be close to working.

------------------------------------------------------------------------

# 50. 10:30--12:30 --- Core Backend

Person 1:

``` text
database
ShowSeat
hold transaction
concurrency correctness
```

Person 2:

``` text
payment
gateway
callback
idempotency
```

Person 3:

``` text
frontend
Docker
CI
```

By lunch:

``` text
movie browsing works
show browsing works
seat map works
seat hold works
payment path mostly works
```

------------------------------------------------------------------------

# 51. 12:30--13:30 --- Rolling Lunch

Never have the entire team stop simultaneously.

One person eats while two continue.

------------------------------------------------------------------------

# 52. 13:30--14:30 --- Core Functionality Complete

Target:

``` text
browse
→ seat map
→ hold
→ pay
→ callback
→ confirmation
```

must work end-to-end.

Run:

``` text
success
failure
duplicate
timeout
race
```

tests.

------------------------------------------------------------------------

# 53. 14:30--16:00 --- Testing + Containerization

Target:

``` text
unit tests
concurrency test
Docker build
Docker Compose
environment variables
health endpoint
```

At **16:00**, the application should already be containerized.

Do not postpone this.

------------------------------------------------------------------------

# 54. 16:00--17:00 --- Deployment

Deploy to Poridhi VM.

Verify:

``` text
public URL
health
movies
shows
seat map
hold
payment
callback
```

Do not wait until 17:30 to start deployment.

------------------------------------------------------------------------

# 55. 17:00--17:30 --- Required Scenarios

Run:

### Scenario A

``` text
100 concurrent requests
same seat
```

Record:

``` text
1 success
99 rejected
0 oversell
```

### Scenario B

``` text
hold
wait for TTL
verify available
rebook
```

Fix any issues.

------------------------------------------------------------------------

# 56. 17:30--18:00 --- Documentation

Finish:

``` text
README.md
DECISIONS.md
architecture diagram
API examples
deployment URL
test results
known limitations
```

------------------------------------------------------------------------

# 57. 18:00--18:20 --- Final Hardening

Check:

``` text
[ ] docker compose up
[ ] /health
[ ] database initializes
[ ] frontend works
[ ] hold works
[ ] payment works
[ ] duplicate callback safe
[ ] forced failure safe
[ ] forced timeout safe
[ ] forced race safe
[ ] TTL configurable
[ ] no secrets committed
[ ] CI green
[ ] deployed URL works
[ ] repository public
```

------------------------------------------------------------------------

# 58. 18:20--18:30 --- Freeze

Stop adding risky features.

Push final commits.

Submit.

Do not introduce:

``` text
new architecture
new database
new authentication system
new UI framework
```

in the final ten minutes.

------------------------------------------------------------------------

# 59. Presentation Plan

The problem says there are no slides.

Use:

``` text
Live demo
+
one architecture diagram
```

## 5-minute structure

### 0:00--0:45

Explain the problem:

> CinemaSeat must survive a burst of buyers fighting for the same seat
> without double-booking.

### 0:45--1:45

Show architecture.

Explain:

``` text
Frontend
→ Spring Boot modules
→ PostgreSQL
→ Gateway
```

### 1:45--3:00

Live happy-path demo:

``` text
movie
→ show
→ seat
→ hold
→ payment
→ callback
→ confirmation
```

### 3:00--4:00

Show Scenario A:

``` text
100 concurrent requests
1 success
99 rejected
0 oversell
```

Then Scenario B:

``` text
hold
→ expiration
→ seat available
→ another user books
```

### 4:00--5:00

Explain:

-   idempotent callbacks
-   database row locking
-   asynchronous payment
-   Docker/CI/deployment

------------------------------------------------------------------------

# 60. Questions Judges Are Likely to Ask

Prepare concise answers to:

## Why PostgreSQL?

Because the critical booking state requires transactional consistency
and row-level locking, and the seat inventory is naturally relational.

## Why not Redis?

Because adding Redis would introduce another infrastructure dependency
without being necessary for this single deployed application.

## Why not microservices?

Because the problem does not require them. A modular monolith provides
clear boundaries while keeping deployment and transaction management
simple under the time constraint.

## How do you prevent double booking?

The hold operation runs inside a database transaction and locks the
relevant `ShowSeat` row. Concurrent requests for that exact seat
therefore serialize.

## What happens if two users request F12 simultaneously?

One transaction acquires the row lock and changes it to `HELD`. The
second transaction then sees `HELD` and returns `409 Conflict`.

## What if the payment callback arrives twice?

The callback is idempotent. `event_id` is unique, and already-processed
events are treated as successful no-ops. The callback still returns 200.

## What if `/charge` times out?

We do not assume payment failure. The payment remains pending and the
callback can later determine the final state.

## What if the callback arrives before `/charge` returns?

The callback updates the durable payment/booking state. The later
`/charge` response must not overwrite a state that was already
finalized.

## What breaks first under more traffic?

Expected answer should be based on the actual load test. Do not invent
it.

Potential bottleneck:

``` text
database row contention
connection pool
CPU
```

depending on measurements.

## What did you leave out?

Say exactly what you cut and why.

For example:

> We deliberately did not implement a cinema admin portal because the
> problem provides pre-populated data and explicitly says an admin
> portal is unnecessary. We prioritized booking correctness, payment
> reliability, testing and deployment.

------------------------------------------------------------------------

# 61. Final Engineering Checklist

## Architecture

-   [ ] Modular structure
-   [ ] Clear boundaries
-   [ ] Simple architecture diagram
-   [ ] Database model documented
-   [ ] Booking state machine documented

## Functionality

-   [ ] Movies
-   [ ] Shows
-   [ ] Seat map
-   [ ] Seat hold
-   [ ] Hold expiration
-   [ ] Payment
-   [ ] Callback
-   [ ] Confirmation
-   [ ] OTP integration

## Concurrency

-   [ ] Database row locking
-   [ ] 100-request same-seat test
-   [ ] Exactly one successful hold
-   [ ] Zero oversell
-   [ ] 409 on contention

## Payment reliability

-   [ ] Async callback
-   [ ] Duplicate callback safe
-   [ ] Failure safe
-   [ ] Timeout safe
-   [ ] Race safe
-   [ ] HTTP 200 from callback

## Code Quality

-   [ ] Validation
-   [ ] Error handling
-   [ ] Logging
-   [ ] Environment configuration
-   [ ] No secrets
-   [ ] Unit tests

## Docker

-   [ ] Dockerfile
-   [ ] docker-compose.yml
-   [ ] PostgreSQL
-   [ ] Gateway
-   [ ] App
-   [ ] Health checks
-   [ ] Clean clone works

## CI

-   [ ] Push workflow
-   [ ] PR workflow
-   [ ] Tests
-   [ ] Docker build
-   [ ] Green default branch

## Deployment

-   [ ] Poridhi VM
-   [ ] Public URL
-   [ ] Health endpoint
-   [ ] End-to-end booking works
-   [ ] Deployment reproducible

## Documentation

-   [ ] README
-   [ ] Architecture diagram
-   [ ] Setup instructions
-   [ ] Deployment instructions
-   [ ] API examples
-   [ ] Exact hold request
-   [ ] Exact seat-map request
-   [ ] Test results
-   [ ] Known limitations
-   [ ] DECISIONS.md

------------------------------------------------------------------------

# 62. Absolute Priority Order

If time becomes limited, use this order:

``` text
1. Seat concurrency correctness
2. Hold + booking state machine
3. Payment callback + idempotency
4. Hold expiration
5. End-to-end happy path
6. Required Scenario A
7. Required Scenario B
8. Unit tests
9. Docker Compose
10. CI
11. Deployment
12. README + DECISIONS.md
13. Minimal frontend polish
14. Bonus features
```

Do **not** sacrifice 1--12 to implement bonus features.

------------------------------------------------------------------------

# 63. Final Principle

The system should be explainable in one sentence:

> **CinemaSeat uses a modular Spring Boot application with PostgreSQL
> transactions and row-level locking to guarantee one owner per
> show-seat, while asynchronous payment callbacks and idempotency make
> the unreliable gateway safe to integrate.**

Everything else should support that statement.

The competition's own final guidance is essentially the same principle:

> **Seats, booking correctness, containers, and a deployable demo come
> before extra features.**

A smaller system that never double-books and is properly shipped is
better than a larger system with a race condition.
