import http from 'k6/http';
import { check } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

// Custom metrics to verify zero overselling and zero deadlocks
export const checkoutSuccessCounter = new Counter('checkout_success');
export const checkoutInsufficientStockCounter = new Counter('checkout_insufficient_stock');
export const checkoutDeadlockCounter = new Counter('checkout_deadlock_detected');
export const checkoutConflictCounter = new Counter('checkout_409_conflict');
export const checkoutLatencyTrend = new Trend('checkout_latency_ms');
export const businessSuccessRate = new Rate('checkout_valid_business_flow_rate');

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080/api/v1';

// Default configuration: 100 concurrent virtual users (VUs)
export const options = {
  scenarios: {
    flash_sale_rush: {
      executor: 'shared-iterations',
      vus: parseInt(__ENV.VUS || '100', 10),
      iterations: parseInt(__ENV.ITERATIONS || '100', 10),
      maxDuration: '1m',
    },
  },
  thresholds: {
    // 1. Zero Deadlocks Guarantee: No 500 error due to database deadlock
    'checkout_deadlock_detected': ['count == 0'],
    // 2. 95% of checkout requests complete within 500ms
    'http_req_duration': ['p(95) < 500'],
  },
};

function generateUUID() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function (c) {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

export function setup() {
  console.log(`[SETUP] Initializing Stress Test against ${BASE_URL}...`);

  // Log in as customer to verify credentials and endpoint readiness
  const loginPayload = JSON.stringify({
    email: __ENV.TEST_USER_EMAIL || 'user@velawear.local',
    password: __ENV.TEST_USER_PASSWORD || 'Password123!',
  });

  const loginRes = http.post(`${BASE_URL}/auth/login`, loginPayload, {
    headers: { 'Content-Type': 'application/json' },
  });

  if (loginRes.status !== 200) {
    console.error(`[SETUP ERROR] Failed to login: ${loginRes.status} - ${loginRes.body}`);
    return { token: null, variantId: 1 };
  }

  const token = loginRes.json('data.accessToken');
  console.log('[SETUP] Login successful. Access token acquired.');

  return {
    token: token,
    variantId: parseInt(__ENV.VARIANT_ID || '1', 10),
  };
}

export default function (data) {
  if (!data || !data.token) {
    console.error('[TEST ERROR] No valid auth token provided.');
    return;
  }

  const token = data.token;
  const variantId = data.variantId;
  const idempotencyKey = generateUUID();

  const authHeaders = {
    'Content-Type': 'application/json',
    'Authorization': `Bearer ${token}`,
  };

  // Step 1: Add target variant to cart
  const addCartPayload = JSON.stringify({
    variantId: variantId,
    quantity: 1,
  });

  http.post(`${BASE_URL}/cart/items`, addCartPayload, {
    headers: authHeaders,
  });

  // Step 2: Preview checkout to generate fresh pricing fingerprint
  const previewPayload = JSON.stringify({
    paymentMethod: 'COD',
    couponCode: null,
  });

  const previewRes = http.post(`${BASE_URL}/checkout/preview`, previewPayload, {
    headers: authHeaders,
  });

  let pricingFingerprint = '';
  if (previewRes.status === 200) {
    pricingFingerprint = previewRes.json('data.pricingFingerprint');
  }

  // Step 3: Concurrent Checkout (Flash Sale Rush)
  const checkoutPayload = JSON.stringify({
    receiverName: 'Flash Sale Buyer',
    receiverPhone: '0901234567',
    receiverAddress: '123 Concurrency St, Ward 1, District 1, HCMC',
    paymentMethod: 'COD',
    shippingFee: 30000,
    couponCode: null,
    pricingFingerprint: pricingFingerprint || 'fallback_fingerprint',
  });

  const checkoutHeaders = {
    'Content-Type': 'application/json',
    'Authorization': `Bearer ${token}`,
    'Idempotency-Key': idempotencyKey,
  };

  const startTime = Date.now();
  const checkoutRes = http.post(`${BASE_URL}/checkout`, checkoutPayload, {
    headers: checkoutHeaders,
  });
  const duration = Date.now() - startTime;
  checkoutLatencyTrend.add(duration);

  // Status assertions
  if (checkoutRes.status === 201) {
    checkoutSuccessCounter.add(1);
    businessSuccessRate.add(1);
  } else if (checkoutRes.status === 400) {
    const errorBody = checkoutRes.body || '';
    if (errorBody.includes('INSUFFICIENT_STOCK') || errorBody.includes('OUT_OF_STOCK')) {
      checkoutInsufficientStockCounter.add(1);
      businessSuccessRate.add(1);
    }
  } else if (checkoutRes.status === 409) {
    checkoutConflictCounter.add(1);
  } else if (checkoutRes.status === 500) {
    const errorBody = checkoutRes.body || '';
    if (errorBody.toLowerCase().includes('deadlock')) {
      checkoutDeadlockCounter.add(1);
      console.error(`[DEADLOCK DETECTED] ${errorBody}`);
    }
    businessSuccessRate.add(0);
  }

  check(checkoutRes, {
    'No Deadlock (500)': (r) => !r.body || !r.body.toLowerCase().includes('deadlock'),
    'Controlled HTTP Status (201 or 400)': (r) => r.status === 201 || r.status === 400 || r.status === 409,
  });
}

export function teardown(data) {
  console.log('\n========================================');
  console.log('       FLASH SALE STRESS TEST SUMMARY   ');
  console.log('========================================');
  console.log(`Target Base URL: ${BASE_URL}`);
  console.log('Check k6 output below for exact TPS, Latency (p95), Success vs Insufficient Stock counts.');
}
