package com.loopers.cache

import com.fasterxml.jackson.core.type.TypeReference

interface CacheTemplate {
    /**
     * 캐시 데이터를 조회합니다.
     *
     * @param cacheKey 캐시 키
     * @param typeReference 캐시 데이터 타입
     * @return 캐시 데이터
     */
    fun <T> get(cacheKey: CacheKey, typeReference: TypeReference<T>): T?

    /**
     * 캐시 데이터를 Bulk 조회합니다.
     *
     * 해당 키로 조회 후, 존재하는 캐시 데이터만 리스트로 반환합니다.
     *
     * @param cacheKeys 캐시 키 리스트
     * @param typeReference 캐시 데이터 타입
     * @return 캐시 데이터 리스트
     */
    fun <T, KEY : CacheKey> getAll(cacheKeys: List<KEY>, typeReference: TypeReference<T>): List<T>

    /**
     * 캐시 데이터를 저장합니다.
     *
     * @param cacheKey 캐시 키
     * @param value 캐시 데이터
     */
    fun <T> put(cacheKey: CacheKey, value: T)

    /**
     * 캐시 데이터를 Bulk 저장합니다.
     *
     * @param cacheMap 캐시 데이터 맵
     */
    fun <T, KEY : CacheKey> putAll(cacheMap: Map<KEY, T>)

    /**
     * 캐시 데이터를 삭제합니다.
     *
     * @param cacheKey 캐시 키
     */
    fun evict(cacheKey: CacheKey)

    /**
     * 캐시 데이터를 Bulk 삭제합니다.
     *
     * @param cacheKeys 캐시 키 리스트
     */
    fun evictAll(cacheKeys: List<CacheKey>)

    /**
     * 캐시 데이터를 조회하고, 없으면 블록을 실행하여 캐시 데이터를 저장합니다.
     *
     * @param cacheKey 캐시 키
     * @param typeReference 캐시 데이터 타입
     * @param block 캐시 데이터 생성 블록
     * @return 캐시 데이터
     */
    fun <T> cacheAside(cacheKey: CacheKey, typeReference: TypeReference<T>, block: () -> T): T
}
