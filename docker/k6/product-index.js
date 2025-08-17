import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Rate } from 'k6/metrics';

export const options = {
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<200'],
  },
  scenarios: {
    runner: {
      executor: 'constant-arrival-rate',
      rate: 50,
      timeUnit: '1s',
      duration: '1m',
      preAllocatedVUs: 8,
      maxVUs: 100,
      exec: 'default',
    },
  },
};

const SORT_OPTIONS = ['LATEST', 'CREATED_AT_ASC', 'CREATED_AT_DESC', 'PRICE_ASC', 'PRICE_DESC', 'LIKES_ASC', 'LIKES_DESC'];
const MAX_PAGE = 1000;
const MAX_BRAND_ID = 500;
const BASE_URL = 'http://host.docker.internal:8080';
const BRAND_ID = '100';
const LIMIT = '20';
const PAGE = '10';  // 기존 OFFSET 의미

const sizeTrend = new Trend('response_size_bytes');
const okRate = new Rate('ok_rate');

function get(path, tags = {}) {
  const res = http.get(`${BASE_URL}${path}`, {
    tags,
    headers: { Accept: 'application/json' },
  });
  const ok = check(res, {
    'status 200': (r) => r.status === 200,
    'json': (r) => String(r.headers['Content-Type'] || '').includes('application/json'),
  });
  okRate.add(ok);
  sizeTrend.add(Number(res.headers['Content-Length'] || res.body?.length || 0));
  return res;
}

export function latest() {
  get(`/api/v1/products?sort=LATEST&size=${LIMIT}&page=${PAGE}`, { scenario: 'latest' });
  sleep(0.1);
}
export function price() {
  get(`/api/v1/products?sort=PRICE_DESC&size=${LIMIT}&page=${PAGE}`, { scenario: 'price' });
  sleep(0.1);
}
export function likes() {
  get(`/api/v1/products?sort=LIKES_DESC&size=${LIMIT}&page=${PAGE}`, { scenario: 'likes' });
  sleep(0.1);
}
export function brandLatest() {
  get(`/api/v1/products?brandId=${BRAND_ID}&sort=LATEST&size=${LIMIT}&page=${PAGE}`, { scenario: 'brand_latest' });
  sleep(0.1);
}
export function brandPrice() {
  get(`/api/v1/products?brandId=${BRAND_ID}&sort=PRICE_DESC&size=${LIMIT}&page=${PAGE}`, { scenario: 'brand_price' });
  sleep(0.1);
}
export function random() {
  const randomSort = SORT_OPTIONS[Math.floor(Math.random() * SORT_OPTIONS.length)];
  const randomPage = Math.floor(Math.random() * MAX_PAGE);
  const randomBrandId = Math.random() < 0.3
      ? `&brandId=${Math.floor(Math.random() * MAX_BRAND_ID) + 1}`
      : '';
  get(`/api/v1/products?sort=${randomSort}&size=${LIMIT}&page=${randomPage}${randomBrandId}`, { scenario: 'random' });
  sleep(0.1);
}

export default function () {
  return random();
}