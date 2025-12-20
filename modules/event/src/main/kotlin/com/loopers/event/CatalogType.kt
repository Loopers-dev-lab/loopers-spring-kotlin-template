package com.loopers.event

enum class CatalogType(private val description: String) {
    LIKED("좋아요 추가"),
    UNLIKED("좋아요 취소"),
    DETAIL_VIEW("상품 상세 조회"),
}
