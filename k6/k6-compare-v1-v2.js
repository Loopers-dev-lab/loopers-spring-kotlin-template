import http from 'k6/http';
import { check } from 'k6';
import { Trend } from 'k6/metrics';

// Custom metrics for comparison
const v1Duration = new Trend('v1_duration', true);
const v2Duration = new Trend('v2_duration', true);
const v1FilteredDuration = new Trend('v1_filtered_duration', true);
const v2FilteredDuration = new Trend('v2_filtered_duration', true);

export const options = {
  scenarios: {
    v1_vs_v2_comparison: {
      executor: 'constant-vus',
      vus: 50,
      duration: '2m',
    },
  },
  thresholds: {
    'v1_duration': ['p(95)<300', 'p(99)<500'],
    'v2_duration': ['p(95)<800', 'p(99)<1500'],
    'v1_filtered_duration': ['p(95)<300', 'p(99)<500'],
    'v2_filtered_duration': ['p(95)<800', 'p(99)<1500'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export default function () {
  // 동일한 조건으로 V1과 V2를 비교하기 위해 파라미터를 먼저 생성

  // Test 1: 필터 없이 동일한 페이지 조회
  const pageNoFilter = Math.floor(Math.random() * 100);
  testV1NoFilter(pageNoFilter);
  testV2NoFilter(pageNoFilter);

  // Test 2: 동일한 브랜드 필터로 동일한 페이지 조회
  const pageWithFilter = Math.floor(Math.random() * 100);
  const brandId = Math.floor(Math.random() * 1000) + 1;
  testV1WithFilter(pageWithFilter, brandId);
  testV2WithFilter(pageWithFilter, brandId);
}

function testV1NoFilter(page) {
  const url = `${BASE_URL}/api/products/v1?page=${page}&size=20&sort=likeCount,desc`;

  const res = http.get(url, {
    tags: { name: 'V1_NoFilter' },
  });

  v1Duration.add(res.timings.duration);

  check(res, {
    'V1 NoFilter: status is 200': (r) => r.status === 200,
  });
}

function testV2NoFilter(page) {
  const url = `${BASE_URL}/api/products/v2?page=${page}&size=20&sort=likeCount,desc`;

  const res = http.get(url, {
    tags: { name: 'V2_NoFilter' },
  });

  v2Duration.add(res.timings.duration);

  check(res, {
    'V2 NoFilter: status is 200': (r) => r.status === 200,
  });
}

function testV1WithFilter(page, brandId) {
  const url = `${BASE_URL}/api/products/v1?page=${page}&size=20&brandId=${brandId}&sort=likeCount,desc`;

  const res = http.get(url, {
    tags: { name: 'V1_WithFilter' },
  });

  v1FilteredDuration.add(res.timings.duration);

  check(res, {
    'V1 WithFilter: status is 200': (r) => r.status === 200,
  });
}

function testV2WithFilter(page, brandId) {
  const url = `${BASE_URL}/api/products/v2?page=${page}&size=20&brandId=${brandId}&sort=likeCount,desc`;

  const res = http.get(url, {
    tags: { name: 'V2_WithFilter' },
  });

  v2FilteredDuration.add(res.timings.duration);

  check(res, {
    'V2 WithFilter: status is 200': (r) => r.status === 200,
  });
}

export function handleSummary(data) {
  console.log('\n========================================');
  console.log('V1 vs V2 Performance Comparison');
  console.log('========================================\n');

  try {
    // Without Filter 비교
    if (data.metrics.v1_duration && data.metrics.v2_duration) {
      const v1 = data.metrics.v1_duration.values || {};
      const v2 = data.metrics.v2_duration.values || {};

      console.log('Without Filter (Same Page):');

      if (v1.avg && v2.avg) {
        console.log(`  V1 - Avg: ${v1.avg.toFixed(2)}ms, P95: ${(v1['p(95)'] || 0).toFixed(2)}ms, P99: ${(v1['p(99)'] || 0).toFixed(2)}ms`);
        console.log(`  V2 - Avg: ${v2.avg.toFixed(2)}ms, P95: ${(v2['p(95)'] || 0).toFixed(2)}ms, P99: ${(v2['p(99)'] || 0).toFixed(2)}ms`);

        const diffPct = ((v2.avg - v1.avg) / v1.avg * 100);
        console.log(`  → V2 is ${Math.abs(diffPct).toFixed(1)}% ${diffPct > 0 ? 'SLOWER' : 'FASTER'} than V1`);
        console.log(`  → V2/V1 ratio: ${(v2.avg / v1.avg).toFixed(2)}x`);
      } else {
        console.log('  ⚠️  Stats incomplete');
      }
    }

    console.log('');

    // With Filter 비교
    if (data.metrics.v1_filtered_duration && data.metrics.v2_filtered_duration) {
      const v1f = data.metrics.v1_filtered_duration.values || {};
      const v2f = data.metrics.v2_filtered_duration.values || {};

      console.log('With Brand Filter (Same Page & Same BrandId):');

      if (v1f.avg && v2f.avg) {
        console.log(`  V1 - Avg: ${v1f.avg.toFixed(2)}ms, P95: ${(v1f['p(95)'] || 0).toFixed(2)}ms, P99: ${(v1f['p(99)'] || 0).toFixed(2)}ms`);
        console.log(`  V2 - Avg: ${v2f.avg.toFixed(2)}ms, P95: ${(v2f['p(95)'] || 0).toFixed(2)}ms, P99: ${(v2f['p(99)'] || 0).toFixed(2)}ms`);

        const diffPct = ((v2f.avg - v1f.avg) / v1f.avg * 100);
        console.log(`  → V2 is ${Math.abs(diffPct).toFixed(1)}% ${diffPct > 0 ? 'SLOWER' : 'FASTER'} than V1`);
        console.log(`  → V2/V1 ratio: ${(v2f.avg / v1f.avg).toFixed(2)}x`);
      } else {
        console.log('  ⚠️  Stats incomplete');
      }
    }

  } catch (err) {
    console.log('\n⚠️  Error:', err.message);
  }

  console.log('\n========================================\n');

  return {
    'stdout': '', // Keep default stdout behavior
  };
}
