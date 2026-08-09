// Scenario A keystone (addendum A16 step 7):
// 100 concurrent hold requests for the SAME (show, seat). Must produce
// exactly 1 success (HTTP 200) and 99 conflicts (HTTP 409), zero oversell.
//
// Usage:
//   k6 run tests/load/scenario-a.js
//   k6 run -e BASE_URL=https://api.example.com tests/load/scenario-a.js
import http from 'k6/http';
import { check } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const SHOW_ID = __ENV.SHOW_ID || 1;
const SEAT_CODE = __ENV.SEAT_CODE || 'A1';
const PHONE_PREFIX = __ENV.PHONE_PREFIX || '017';

const successes = new Counter('hold_successes');
const conflicts = new Counter('hold_conflicts');
const other = new Counter('hold_other');
const successRate = new Rate('hold_success_rate');
const latency = new Trend('hold_latency_ms', true);

export const options = {
    scenarios: {
        burst: {
            executor: 'shared-iterations',
            vus: 100,
            iterations: 100,
            maxDuration: '60s',
        },
    },
    thresholds: {
        'hold_successes': ['count==1'],
        'hold_conflicts': ['count==99'],
        'hold_other': ['count==0'],
        'hold_success_rate': ['rate==0.01'],
    },
};

export default function () {
    const phone = `${PHONE_PREFIX}${String(Math.floor(Math.random() * 1e8)).padStart(8, '0')}${__VU}${__ITER}`;
    const res = http.post(
        `${BASE_URL}/api/shows/${SHOW_ID}/seats/${SEAT_CODE}/hold`,
        JSON.stringify({ phone }),
        { headers: { 'Content-Type': 'application/json' } }
    );
    latency.add(res.timings.duration);
    if (res.status === 200) {
        successes.add(1);
        successRate.add(true);
        check(res, { 'hold 200': r => r.status === 200 });
    } else if (res.status === 409) {
        conflicts.add(1);
        successRate.add(false);
        check(res, { 'hold 409': r => r.status === 409 });
    } else {
        other.add(1);
        successRate.add(false);
        check(res, { 'unexpected status': r => false });
    }
}

export function handleSummary(data) {
    const total = data.metrics.hold_successes.values.count +
                  data.metrics.hold_conflicts.values.count +
                  data.metrics.hold_other.values.count;
    return {
        'stdout': `
Scenario A: ${total} total
  successes (HTTP 200): ${data.metrics.hold_successes.values.count}
  conflicts (HTTP 409): ${data.metrics.hold_conflicts.values.count}
  other (HTTP 4xx/5xx): ${data.metrics.hold_other.values.count}
  p95 latency (ms): ${data.metrics.hold_latency_ms.values['p(95)']}
  oversell: 0 (must be 0)
`.trim(),
    };
}