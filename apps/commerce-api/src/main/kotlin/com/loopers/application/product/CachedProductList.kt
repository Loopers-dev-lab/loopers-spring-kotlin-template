package com.loopers.application.product

/**
 * 캐시된 상품 목록
 *
 * @property productIds 상품 ID 목록
 * @property hasNext 다음 페이지 존재 여부
 */
data class CachedProductList(
    val productIds: List<Long>,
    val hasNext: Boolean,
)
