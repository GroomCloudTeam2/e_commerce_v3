import http from 'k6/http';
import { check, fail, sleep } from 'k6';
import { Counter } from 'k6/metrics';

const TOTAL_VUS = Number(__ENV.VUS || 10);
const ITERATIONS_PER_VU = Number(__ENV.ITERATIONS || 1);
const WAVE_GAP_SEC = Number(__ENV.WAVE_GAP_SEC || 5);
const wave1Vus = Number(__ENV.WAVE1_VUS || Math.max(1, Math.floor(TOTAL_VUS * 0.3)));
const wave2Vus = Number(__ENV.WAVE2_VUS || Math.max(1, Math.floor(TOTAL_VUS * 0.3)));
const wave3Vus = Math.max(0, TOTAL_VUS - wave1Vus - wave2Vus);

const scenarios = {
  wave1: {
    executor: 'per-vu-iterations',
    exec: 'e2eFlow',
    startTime: '0s',
    vus: wave1Vus,
    iterations: ITERATIONS_PER_VU,
    maxDuration: __ENV.MAX_DURATION || '10m',
  },
  wave2: {
    executor: 'per-vu-iterations',
    exec: 'e2eFlow',
    startTime: `${WAVE_GAP_SEC}s`,
    vus: wave2Vus,
    iterations: ITERATIONS_PER_VU,
    maxDuration: __ENV.MAX_DURATION || '10m',
  },
};

if (wave3Vus > 0) {
  scenarios.wave3 = {
    executor: 'per-vu-iterations',
    exec: 'e2eFlow',
    startTime: `${WAVE_GAP_SEC * 2}s`,
    vus: wave3Vus,
    iterations: ITERATIONS_PER_VU,
    maxDuration: __ENV.MAX_DURATION || '10m',
  };
}

export const options = { scenarios };

const BASE_URL = __ENV.BASE_URL || 'http://k8s-istiosys-devgwist-75f9e87595-cb185c46783cf9f8.elb.ap-northeast-2.amazonaws.com';
const TOKEN = __ENV.TOKEN;

const HOST_PRODUCT = __ENV.HOST_PRODUCT || 'product-dev.example.com';
const HOST_CART = __ENV.HOST_CART || 'cart-dev.example.com';
const HOST_ORDER = __ENV.HOST_ORDER || 'order-dev.example.com';
const HOST_PAYMENT = __ENV.HOST_PAYMENT || 'payment-dev.example.com';
const paymentReadyRetryFailures = new Counter('payment_ready_retry_failures');

if (!TOKEN) {
  fail('TOKEN env is required');
}

function req(method, host, path, body, expectedStatuses) {
  const headers = {
    Authorization: `Bearer ${TOKEN}`,
    Host: host,
  };

  if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }

  const payload = body !== undefined ? JSON.stringify(body) : null;
  const params = { headers };
  if (Array.isArray(expectedStatuses) && expectedStatuses.length > 0) {
    params.responseCallback = http.expectedStatuses(...expectedStatuses);
  }
  const res = http.request(method, `${BASE_URL}${path}`, payload, params);
  return res;
}

export function e2eFlow() {
  let res;

  res = req('GET', HOST_PRODUCT, '/api/v2/products?page=1&size=20');
  check(res, { 'STEP1 product list code=200': (r) => r.status === 200 }) || fail(`STEP1 failed: ${res.status} ${res.body}`);
  const productList = res.json();
  const productId = productList?.content?.[0]?.productId;
  if (!productId) fail('STEP1 failed: productId not found');

  res = req('GET', HOST_PRODUCT, `/api/v2/products/${productId}`);
  check(res, { 'STEP2 product detail code=200': (r) => r.status === 200 }) || fail(`STEP2 failed: ${res.status} ${res.body}`);
  const detail = res.json();
  const price = detail?.price ?? detail?.variants?.[0]?.price ?? 0;
  const title = detail?.title ?? '';
  const thumb = detail?.thumbnailUrl ?? '';
  const optionName = detail?.variants?.[0]?.optionName ?? '';
  const variantId = detail?.variants?.[0]?.variantId ?? null;
  if (!price) fail(`STEP2 failed: invalid price=${price}`);

  res = req('POST', HOST_CART, '/api/v2/cart', {
    productId,
    variantId,
    quantity: 1,
  });
  check(res, { 'STEP3 cart add code=201': (r) => r.status === 201 }) || fail(`STEP3 failed: ${res.status} ${res.body}`);

  res = req('GET', HOST_CART, '/api/v2/cart');
  check(res, { 'STEP4 cart get code=200': (r) => r.status === 200 }) || fail(`STEP4 failed: ${res.status} ${res.body}`);
  const cart = res.json();
  if (!Array.isArray(cart) || cart.length < 1) fail(`STEP4 failed: invalid cart=${res.body}`);

  res = req('POST', HOST_ORDER, '/api/v2/orders', {
    addressId: null,
    totalAmount: price,
    items: [
      {
        productId,
        variantId,
        quantity: 1,
        productTitle: title,
        productThumbnail: thumb,
        optionName,
        unitPrice: price,
      },
    ],
  });
  check(res, { 'STEP5 order create code=200': (r) => r.status === 200 }) || fail(`STEP5 failed: ${res.status} ${res.body}`);
  const orderId = res.json()?.orderId;
  if (!orderId) fail(`STEP5 failed: orderId missing body=${res.body}`);

  let readyOk = false;
  for (let i = 0; i < 5; i += 1) {
    res = req('POST', HOST_PAYMENT, '/api/v2/payments/ready', {
      orderId,
      amount: price,
    }, [200, 502, 503, 504]);
    if (res.status === 200) {
      readyOk = true;
      break;
    }
    paymentReadyRetryFailures.add(1);
    sleep(1);
  }
  if (!readyOk) fail(`STEP6 failed: payments/ready not 200, last=${res.status} ${res.body}`);

  const deleteItems = cart.map((x) => ({
    productId: x.productId,
    variantId: x.variantId ?? null,
  }));
  res = req('DELETE', HOST_CART, '/api/v2/cart/items/bulk', deleteItems);
  check(res, { 'STEP7 cart clear code=204': (r) => r.status === 204 }) || fail(`STEP7 failed: ${res.status} ${res.body}`);

  res = req('GET', HOST_CART, '/api/v2/cart');
  check(res, { 'STEP8 cart empty code=200': (r) => r.status === 200 }) || fail(`STEP8 failed: ${res.status} ${res.body}`);
  const finalCart = res.json();
  if (!Array.isArray(finalCart) || finalCart.length !== 0) {
    fail(`STEP8 failed: cart not empty body=${res.body}`);
  }
}

export default function () {
  e2eFlow();
}

export function handleSummary(data) {
  const checks = data.metrics.checks || {};
  const iterations = data.metrics.iterations || {};
  const httpReqs = data.metrics.http_reqs || {};
  const httpFailed = data.metrics.http_req_failed || {};
  const checkPasses = checks.values?.passes ?? 0;
  const checkFails = checks.values?.fails ?? 0;
  const checkTotal = checkPasses + checkFails;

  const txt = [
    'k6 e2e summary',
    `checks: ${checkPasses}/${checkTotal} passed`,
    `iterations: ${iterations.values?.count ?? 0}`,
    `http_reqs: ${httpReqs.values?.count ?? 0}`,
    `http_req_failed_rate: ${httpFailed.values?.rate ?? 0}`,
  ].join('\n');

  return {
    'k6_e2e_summary.json': JSON.stringify(data, null, 2),
    'k6_e2e_summary.txt': `${txt}\n`,
  };
}
