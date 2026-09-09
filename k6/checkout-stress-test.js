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
const TOTAL_VUS = parseInt(__ENV.VUS || '200', 10);
const VARIANT_ID = parseInt(__ENV.VARIANT_ID || '1', 10);

export const options = {
  scenarios: {
    flash_sale_rush: {
      executor: 'per-vu-iterations',
      vus: TOTAL_VUS,
      iterations: 1,
      maxDuration: '2m',
    },
  },
  thresholds: {
    // 100% Zero Deadlocks
    'checkout_deadlock_detected': ['count == 0'],
  },
};

function generateUUID() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function (c) {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

// Pre-authenticate all buyers in batches
export function setup() {
  console.log(`[SETUP] Authenticating ${TOTAL_VUS} test buyers in parallel batches...`);
  const tokens = new Array(TOTAL_VUS);
  const BATCH_SIZE = 50;

  for (let b = 0; b < TOTAL_VUS; b += BATCH_SIZE) {
    const end = Math.min(b + BATCH_SIZE, TOTAL_VUS);
    const requests = [];

    for (let i = b + 1; i <= end; i++) {
      requests.push({
        method: 'POST',
        url: `${BASE_URL}/auth/login`,
        body: JSON.stringify({ email: `buyer_${i}@test.local`, password: 'Password123!' }),
        params: { headers: { 'Content-Type': 'application/json' } },
      });
    }

    const responses = http.batch(requests);
    responses.forEach((res, index) => {
      const buyerIndex = b + index;
      if (res.status === 200) {
        tokens[buyerIndex] = res.json('data.accessToken');
      } else {
        tokens[buyerIndex] = null;
      }
    });

    sleep(0.1);
  }

  const validCount = tokens.filter(t => t !== null).length;
  console.log(`[SETUP] Authenticated ${validCount}/${TOTAL_VUS} buyers successfully.`);
  return { tokens };
}

export default function (data) {
  const vuId = __VU;
  const token = data.tokens[vuId - 1];

  if (!token) {
    return;
  }

  const authHeaders = {
    'Content-Type': 'application/json',
    'Authorization': `Bearer ${token}`,
  };

  // Step 1: Add item to cart
  const cartRes = http.put(
    `${BASE_URL}/carts/me/items`,
    JSON.stringify({ items: [{ variantId: VARIANT_ID, quantity: 1 }] }),
    { headers: authHeaders }
  );

  if (cartRes.status !== 200) {
    return;
  }

  // Step 2: Preview checkout
  const previewRes = http.post(
    `${BASE_URL}/checkout/preview`,
    JSON.stringify({ paymentMethod: 'COD', couponCode: null }),
    { headers: authHeaders }
  );

  if (previewRes.status !== 200) {
    return;
  }

  const pricingFingerprint = previewRes.json('data.pricingFingerprint');

  // Step 3: Concurrent Checkout
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
