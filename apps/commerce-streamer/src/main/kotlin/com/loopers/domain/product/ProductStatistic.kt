package com.loopers.domain.product

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * ProductStatistic 엔티티 - 상품 통계 정보
 *
 * - BaseEntity를 상속하지 않음 (인프라 데이터)
 * - commerce-api와 동일한 테이블(product_statistics)을 참조
 */
@Entity
@Table(name = "product_statistics")
class ProductStatistic(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "product_id", nullable = false)
    val productId: Long,

    @Column(name = "like_count", nullable = false)
    var likeCount: Long = 0,

    @Column(name = "sales_count", nullable = false)
    var salesCount: Long = 0,

    @Column(name = "view_count", nullable = false)
    var viewCount: Long = 0,
)
