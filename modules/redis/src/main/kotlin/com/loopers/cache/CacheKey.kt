package com.loopers.cache

import java.time.Duration

/**
 * 캐시 키 인터페이스
 *
 * 캐시 키는 해당 인터페이스를 기반으로 구현되어야 합니다.
 *
 * @property key 캐시 키 (e.g. product:1)
 * @property traceKey 캐시 추적 키 (e.g. product)
 * @property ttl 캐시 TTL (Time To Live)
 */
interface CacheKey {
    val key: String
    val traceKey: String
    val ttl: Duration
}
