-- V1__init.sql -- CinemaSeat schema (addendum A6, A7)
-- All timestamps are TIMESTAMPTZ. Idempotency through UNIQUE constraints.
-- Lock ordering (addendum A2): Payment -> Booking -> ShowSeat (ascending by id).

CREATE TABLE movie (
    id               BIGSERIAL PRIMARY KEY,
    title            TEXT NOT NULL,
    description      TEXT,
    duration_minutes INTEGER NOT NULL CHECK (duration_minutes > 0),
    language         TEXT,
    rating           TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE theatre (
    id          BIGSERIAL PRIMARY KEY,
    name        TEXT NOT NULL,
    location    TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE screen (
    id          BIGSERIAL PRIMARY KEY,
    theatre_id  BIGINT NOT NULL REFERENCES theatre(id) ON DELETE CASCADE,
    name        TEXT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE seat (
    id          BIGSERIAL PRIMARY KEY,
    screen_id   BIGINT NOT NULL REFERENCES screen(id) ON DELETE CASCADE,
    row_label   TEXT NOT NULL,
    seat_number INTEGER NOT NULL CHECK (seat_number > 0),
    seat_code   TEXT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (screen_id, row_label, seat_number),
    UNIQUE (screen_id, seat_code)
);

CREATE TABLE show (
    id           BIGSERIAL PRIMARY KEY,
    movie_id     BIGINT NOT NULL REFERENCES movie(id) ON DELETE CASCADE,
    screen_id    BIGINT NOT NULL REFERENCES screen(id) ON DELETE CASCADE,
    start_time   TIMESTAMPTZ NOT NULL,
    end_time     TIMESTAMPTZ NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (end_time > start_time)
);

CREATE INDEX idx_show_movie ON show(movie_id);
CREATE INDEX idx_show_screen ON show(screen_id);

-- The critical table. Status enum, hold_token reference, optimistic version column DROPPED per addendum A1.
-- Pessimistic FOR UPDATE on the hold path is the only locking strategy.
CREATE TABLE show_seat (
    id                  BIGSERIAL PRIMARY KEY,
    show_id             BIGINT NOT NULL REFERENCES show(id) ON DELETE CASCADE,
    seat_id             BIGINT NOT NULL REFERENCES seat(id) ON DELETE CASCADE,
    price               NUMERIC(10,2) NOT NULL CHECK (price >= 0),
    status              TEXT NOT NULL DEFAULT 'AVAILABLE'
                            CHECK (status IN ('AVAILABLE','HELD','BOOKED')),
    hold_expires_at     TIMESTAMPTZ,
    booking_id          BIGINT,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (show_id, seat_id)
);

CREATE INDEX idx_show_seat_show ON show_seat(show_id);
CREATE INDEX idx_show_seat_booking ON show_seat(booking_id);
CREATE INDEX idx_show_seat_held ON show_seat(show_id) WHERE status = 'HELD';
CREATE INDEX idx_show_seat_available ON show_seat(show_id) WHERE status = 'AVAILABLE';

CREATE TABLE booking (
    id              BIGSERIAL PRIMARY KEY,
    booking_ref     TEXT NOT NULL UNIQUE,
    show_id         BIGINT NOT NULL REFERENCES show(id),
    user_phone      TEXT NOT NULL,
    total_amount    NUMERIC(10,2) NOT NULL CHECK (total_amount >= 0),
    status          TEXT NOT NULL DEFAULT 'PENDING_PAYMENT'
                        CHECK (status IN ('PENDING_PAYMENT','CONFIRMED','PAYMENT_FAILED','EXPIRED','REFUNDED','CANCELLED')),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_booking_phone ON booking(user_phone);
CREATE INDEX idx_booking_show ON booking(show_id);
CREATE INDEX idx_booking_status ON booking(status);

-- Add the FK from show_seat to booking now that booking exists.
ALTER TABLE show_seat
    ADD CONSTRAINT fk_show_seat_booking
    FOREIGN KEY (booking_id) REFERENCES booking(id) ON DELETE SET NULL;

CREATE TABLE payment (
    id              BIGSERIAL PRIMARY KEY,
    payment_id      TEXT NOT NULL UNIQUE,
    booking_id      BIGINT NOT NULL UNIQUE REFERENCES booking(id),
    amount          NUMERIC(10,2) NOT NULL CHECK (amount >= 0),
    currency        TEXT NOT NULL DEFAULT 'BDT',
    status          TEXT NOT NULL DEFAULT 'PENDING'
                        CHECK (status IN ('PENDING','SUCCEEDED','FAILED','REFUNDED')),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_payment_status ON payment(status);

-- Idempotency layer 1 (addendum B3 / §18): UNIQUE event_id.
CREATE TABLE payment_event (
    id           BIGSERIAL PRIMARY KEY,
    event_id     TEXT NOT NULL UNIQUE,
    payment_id   TEXT NOT NULL,
    booking_ref  TEXT NOT NULL,
    status       TEXT NOT NULL,
    amount       NUMERIC(10,2),
    currency     TEXT,
    raw_payload  TEXT,
    received_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_payment_event_payment ON payment_event(payment_id);
CREATE INDEX idx_payment_event_booking ON payment_event(booking_ref);

-- Hold tokens stored as hashes (addendum A7). Never log raw token.
CREATE TABLE hold_token (
    id          BIGSERIAL PRIMARY KEY,
    token_hash  TEXT NOT NULL UNIQUE,
    booking_id  BIGINT NOT NULL REFERENCES booking(id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    revoked_at  TIMESTAMPTZ
);

CREATE INDEX idx_hold_token_booking ON hold_token(booking_id);

-- OTP verification attempt counter (addendum B12: 429 after 5 attempts).
CREATE TABLE otp_record (
    ref             TEXT PRIMARY KEY,
    phone           TEXT NOT NULL,
    code_hash       TEXT NOT NULL,
    send_attempt    INTEGER NOT NULL DEFAULT 0,
    verify_attempts INTEGER NOT NULL DEFAULT 0,
    delivered       BOOLEAN NOT NULL DEFAULT FALSE,
    verified        BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ
);