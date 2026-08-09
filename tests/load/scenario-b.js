// Scenario B: Hold expiration (addendum §34).
//
// 1. Hold a seat.
// 2. Wait > HOLD_TTL_SECONDS without paying.
// 3. Fetch the seat map — seat must be AVAILABLE again.
// 4. A different user holds the same seat — must succeed.
//
// Usage:
//   k6 run -e HOLD_TTL_SECONDS=10 -e WAIT_SECONDS=15 tests/load/scenario-b.js
import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const SHOW_ID = __ENV.SHOW_ID || 1;
const SEAT_CODE = __ENV.SEAT_CODE || 'A2';
const HOLD_TTL = parseInt(__ENV.HOLD_TTL_SECONDS || '10', 10);
const WAIT = parseInt(__ENV.WAIT_SECONDS || `${HOLD_TTL + 5}`, 10);

export const options = {
    vus: 1,
    iterations: 1,
};

function postHold(phone) {
    return http.post(
        `${BASE_URL}/api/shows/${SHOW_ID}/seats/${SEAT_CODE}/hold`,
        JSON.stringify({ phone }),
        { headers: { 'Content-Type': 'application/json' } }
    );
}

function getSeatMap() {
    return http.get(`${BASE_URL}/api/shows/${SHOW_ID}/seats`);
}

export default function () {
    const phoneA = '01700000001';
    const r1 = postHold(phoneA);
    check(r1, { 'A holds seat (200)': r => r.status === 200 });
    if (r1.status !== 200) {
        console.error('User A failed to hold:', r1.body);
        return;
    }
    console.log(`Waiting ${WAIT}s for hold to expire (TTL=${HOLD_TTL}s)...`);
    sleep(WAIT);
    const r2 = getSeatMap();
    check(r2, { 'seat map 200': r => r.status === 200 });
    const seat = (r2.json('seats') || []).find(s => s.seatCode === SEAT_CODE);
    check(seat, { 'seat AVAILABLE after TTL': s => s && s.status === 'AVAILABLE' });
    const phoneB = '01700000002';
    const r3 = postHold(phoneB);
    check(r3, { 'B re-holds after TTL (200)': r => r.status === 200 });
    console.log(`\nScenario B: A held -> wait ${WAIT}s -> AVAILABLE -> B holds -> ${r3.status === 200 ? 'OK' : 'FAILED'}`);
}