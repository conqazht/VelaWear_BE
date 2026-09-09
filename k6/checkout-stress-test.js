import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend, Rate } from 'k6/metrics';

// Custom metrics
export const checkoutSuccess = new Counter('checkout_success');
export const checkoutInsufficientStock = new Counter('checkout_insufficient_stock');
export const checkoutDeadlockDetected = new Counter('checkout_deadlock_detected');
export const checkoutDuration = new Trend('checkout_duration_ms');
export const transactionConsistencyRate = new Rate('transaction_consistency_rate');

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080/api/v1';
const TOTAL_VUS = parseInt(__ENV.VUS || '50', 10);
const VARIANT_ID = parseInt(__ENV.VARIANT_ID || '1', 10);

export const options = {
  scenarios: {
    flash_sale_rush: {
      executor: 'per-vu-iterations',
      vus: TOTAL_VUS,
      iterations: 1,
      maxDuration: '1m',
    },
  },
  thresholds: {
    // Zero deadlocks guaranteed
    'checkout_deadlock_detected': ['count == 0'],
    // Consistency rate (either 201 Created or 400 Insufficient Stock)
    'transaction_consistency_rate': ['rate == 1.0'],
  },
};

function generateUUID() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function (c) {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

// Pre-login users in setup() to avoid IP login rate-limiting
export function setup() {
  console.log(`[SETUP] Pre-authenticating ${TOTAL_VUS} test buyers...`);
  const tokens = [];

  for (let i = 1; i <= TOTAL_VUS; i++) {
    const email = `buyer_${i}@test.local`;
    const password = 'Password123!';

    const res = http.post(
      `${BASE_URL}/auth/login`,
      JSON.stringify({ email, password }),
      { headers: { 'Content-Type': 'application/json' } }
    );

    if (res.status === 200) {
      tokens.push(res.json('data.accessToken'));
    } else {
      console.warn(`[SETUP] Warning: buyer_${i} login status ${res.status}`);
      tokens.push(null);
    }
    sleep(0.05); // 50ms pause to respect auth rate-limiting
  }

  console.log(`[SETUP] Successfully authenticated ${tokens.filter(t => t !== null).length}/${TOTAL_VUS} buyers.`);
  return { tokens };
}

export default function (data) {
  const vuId = __VU;
  const token = data.tokens[vuId - 1];

  if (!token) {
    console.error(`[VU ${vuId}] Skipped: no valid token from setup.`);
    return;
  }

  const authHeaders = {
    'Content-Type': 'application/json',
    'Authorization': `Bearer ${token}`,
  };

  // Step 1: Put item in cart
  const cartRes = http.put(
    `${BASE_URL}/carts/me/items`,
    JSON.stringify({ items: [{ variantId: VARIANT_ID, quantity: 1 }] }),
    { headers: authHeaders }
  );

  if (cartRes.status !== 200) {
    console.error(`[VU ${vuId}] Update cart failed: ${cartRes.status}`);
    return;
  }

  // Step 2: Preview checkout
  const previewRes = http.post(
    `${BASE_URL}/checkout/preview`,
    JSON.stringify({ paymentMethod: 'COD', couponCode: null }),
    { headers: authHeaders }
  );

  if (previewRes.status !== 200) {
    console.error(`[VU ${vuId}] Preview failed: ${previewRes.status}`);
    return;
  }

  const pricingFingerprint = previewRes.json('data.pricingFingerprint');

  // Step 3: Concurrent Checkout (Flash Sale)
  const idempotencyKey = generateUUID();
  const checkoutPayload = JSON.stringify({
    receiverName: `Buyer ${vuId}`,
    receiverPhone: '0901234567',
    receiverAddress: '123 Concurrency Ave, Ward 1, District 1, HCMC',
    paymentMethod: 'COD',
    shippingFee: 30000,
    couponCode: null,
    pricingFingerprint: pricingFingerprint,
  });

  const checkoutHeaders = {
    'Content-Type': 'application/json',
    'Authorization': `Bearer ${token}`,
    'Idempotency-Key': idempotencyKey,
  };

  const start = Date.now();
  const checkoutRes = http.post(`${BASE_URL}/checkout`, checkoutPayload, {
    headers: checkoutHeaders,
  });
  checkoutDuration.add(Date.now() - start);

  // Assertions & Metrics
  if (checkoutRes.status === 201) {
    checkoutSuccess.add(1);
    transactionConsistencyRate.add(1);
  } else if (checkoutRes.status === 400) {
    const errorBody = checkoutRes.body || '';
    if (errorBody.includes('INSUFFICIENT_STOCK') || errorBody.includes('OUT_OF_STOCK') || errorBody.includes('Cart is empty')) {
      checkoutInsufficientStock.add(1);
      transactionConsistencyRate.add(1);
    }
  } else if (checkoutRes.status === 500) {
    const errorBody = checkoutRes.body || '';
    if (errorBody.toLowerCase().includes('deadlock')) {
      checkoutDeadlockDetected.add(1);
      console.error(`[DEADLOCK DETECTED VU ${vuId}]: ${errorBody}`);
    }
    transactionConsistencyRate.add(0);
  }

  check(checkoutRes, {
    'No Deadlock (500)': (r) => !r.body || !r.body.toLowerCase().includes('deadlock'),
    'Controlled Status (201 Created or 400 Insufficient Stock)': (r) => r.status === 201 || r.status === 400,
  });
}
