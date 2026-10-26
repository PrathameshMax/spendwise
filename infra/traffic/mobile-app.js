// Production Debugging Lab — traffic simulator (k6).
//
// Production is never idle: this stands in for the SpendWise mobile app's
// backend-for-frontend calling transaction-service on the internal network,
// for lab users 1..LAB_USERS (seeded by POST /lab/seed). It runs until
// ./scripts/lab.sh traffic-stop.
//
//   home_screen  GET /api/v1/transactions/insights/monthly   HOME_SCREEN_PER_MIN (default 90/min)
//   statement    GET /api/v1/transactions?userId=..&size=20   STATEMENT_PER_MIN   (default 120/min)
//
// Constant arrival rate: requests keep arriving at the configured pace even
// when responses slow down, exactly like real users opening the app. Slow
// responses therefore pile up instead of slowing the simulator down.
// Every request carries X-Correlation-ID "sim-<scenario>-<time>-<vu>-<iter>",
// so any request can be followed in Splunk.
//
// The simulator's own output is not shipped to Splunk: it is the outside
// world, not part of the platform.

import http from 'k6/http';
import { check } from 'k6';

const TARGET = __ENV.TARGET || 'http://transaction-service:8083';
const LAB_USERS = parseInt(__ENV.LAB_USERS || '1000', 10);
const HOME_SCREEN_PER_MIN = parseInt(__ENV.HOME_SCREEN_PER_MIN || '90', 10);
const STATEMENT_PER_MIN = parseInt(__ENV.STATEMENT_PER_MIN || '120', 10);

export const options = {
  discardResponseBodies: true,
  summaryTrendStats: ['avg', 'p(95)', 'p(99)', 'max'],
  scenarios: {
    home_screen: {
      executor: 'constant-arrival-rate',
      exec: 'homeScreen',
      rate: HOME_SCREEN_PER_MIN,
      timeUnit: '1m',
      duration: '12h',
      preAllocatedVUs: 10,
      maxVUs: 120,
    },
    statement: {
      executor: 'constant-arrival-rate',
      exec: 'statement',
      rate: STATEMENT_PER_MIN,
      timeUnit: '1m',
      duration: '12h',
      preAllocatedVUs: 10,
      maxVUs: 120,
    },
  },
};

function labUserId() {
  const index = 1 + Math.floor(Math.random() * LAB_USERS);
  const hex = index.toString(16).padStart(8, '0');
  return `1ab5eed0-${hex.slice(0, 4)}-${hex.slice(4)}-0000-000000000000`;
}

function params(scenario, name) {
  return {
    timeout: '30s',
    tags: { name },
    headers: { 'X-Correlation-ID': `sim-${scenario}-${Date.now()}-${__VU}-${__ITER}` },
  };
}

export function homeScreen() {
  const month = new Date().toISOString().slice(0, 7);
  const res = http.get(
    `${TARGET}/api/v1/transactions/insights/monthly?userId=${labUserId()}&month=${month}`,
    params('home', 'GET /api/v1/transactions/insights/monthly'));
  check(res, { 'home screen 200': (r) => r.status === 200 });
}

export function statement() {
  const res = http.get(
    `${TARGET}/api/v1/transactions?userId=${labUserId()}&page=0&size=20&sort=transactionDate,desc`,
    params('statement', 'GET /api/v1/transactions'));
  check(res, { 'statement 200': (r) => r.status === 200 });
}
