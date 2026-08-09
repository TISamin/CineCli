-- V2__seed.sql -- Minimal but realistic seed for the demo and Scenario A/B tests.
-- 2 movies, 1 theatre, 2 screens (5 rows x 6 seats = 30 seats per screen), 4 shows.

-- Movies
INSERT INTO movie (id, title, description, duration_minutes, language, rating) VALUES
    (1, 'The Last Signal', 'A lone engineer decodes a transmission from 1987.', 118, 'English', 'PG-13'),
    (2, 'Cinema of Ghosts', 'A documentary about the haunted projection booth.', 92, 'Bengali', 'PG');

-- Theatre
INSERT INTO theatre (id, name, location) VALUES
    (1, 'CinemaStar Downtown', '12 Park Avenue, Dhaka');

-- Screens
INSERT INTO screen (id, theatre_id, name) VALUES
    (1, 1, 'Hall A'),
    (2, 1, 'Hall B');

-- Seats: 5 rows (A..E) x 6 seats = 30 per screen
INSERT INTO seat (screen_id, row_label, seat_number, seat_code)
SELECT s.id, r.row_label, n.seat_number, r.row_label || n.seat_number
FROM screen s
CROSS JOIN (VALUES ('A'),('B'),('C'),('D'),('E')) AS r(row_label)
CROSS JOIN generate_series(1, 6) AS n(seat_number)
WHERE s.id IN (1, 2);

-- Shows: 4 total — 2 per screen, 2 days x 2 times (afternoon, evening).
INSERT INTO show (id, movie_id, screen_id, start_time, end_time) VALUES
    (1, 1, 1, '2026-08-10 14:00:00+00', '2026-08-10 16:00:00+00'),
    (2, 1, 1, '2026-08-10 19:00:00+00', '2026-08-10 21:00:00+00'),
    (3, 2, 2, '2026-08-10 14:30:00+00', '2026-08-10 16:00:00+00'),
    (4, 2, 2, '2026-08-10 19:30:00+00', '2026-08-10 21:00:00+00');

-- Show seats: one per (show, seat). Prices vary by hall.
INSERT INTO show_seat (show_id, seat_id, price, status)
SELECT s.id, seat.id,
       CASE WHEN s.screen_id = 1 THEN 450 ELSE 380 END,
       'AVAILABLE'
FROM show s
JOIN seat ON seat.screen_id = s.screen_id;

-- Update sequences to start above our explicit ids
SELECT setval(pg_get_serial_sequence('movie','id'),    (SELECT MAX(id) FROM movie));
SELECT setval(pg_get_serial_sequence('theatre','id'),  (SELECT MAX(id) FROM theatre));
SELECT setval(pg_get_serial_sequence('screen','id'),   (SELECT MAX(id) FROM screen));
SELECT setval(pg_get_serial_sequence('seat','id'),     (SELECT MAX(id) FROM seat));
SELECT setval(pg_get_serial_sequence('show','id'),     (SELECT MAX(id) FROM show));
SELECT setval(pg_get_serial_sequence('show_seat','id'),(SELECT MAX(id) FROM show_seat));