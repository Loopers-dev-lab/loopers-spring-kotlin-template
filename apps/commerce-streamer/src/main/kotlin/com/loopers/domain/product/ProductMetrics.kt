package com.loopers.domain.product

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import jakarta.persistence.Version

/**
 * 상품별 집계 메트릭 테이블
 * Consumer가 이벤트를 처리하여 실시간 집계 데이터 유지
 */
@Entity
@Table(
    name = "product_metrics",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_product_metrics_product_id",
            columnNames = ["productId"],
        ),
    ],
    indexes = [
        Index(name = "idx_product_metrics_like_count", columnList = "likeCount DESC"),
        Index(name = "idx_product_metrics_view_count", columnList = "viewCount DESC"),
        Index(name = "idx_product_metrics_sales_count", columnList = "salesCount DESC"),
    ],
)
class ProductMetrics(
    /**
     * 상품 ID
     */
    @Column(nullable = false, unique = true)
    val productId: Long,

    /**
     * 좋아요 수
     */
    @Column(nullable = false)
    var likeCount: Long = 0,

    /**
     * 조회 수 (상세 페이지 조회)
     */
    @Column(nullable = false)
    var viewCount: Long = 0,

    /**
     * 판매량 (주문 완료 기준)
     */
    @Column(nullable = false)
    var salesCount: Long = 0,

    /**
     * 총 판매 금액
     */
    @Column(nullable = false)
    var totalSalesAmount: Long = 0,

    /**
     * 낙관적 락 버전 (동시성 제어)
     */
    @Version
    @Column(nullable = false)
    var version: Long = 0,
) : BaseEntity() {

    /**
     * 좋아요 수 증가
     */
    fun incrementLikeCount() {
        this.likeCount++
    }

    /**
     * 좋아요 수 감소
     */
    fun decrementLikeCount() {
        if (this.likeCount > 0) {
            this.likeCount--
        }
    }

    /**
     * 조회 수 증가
     */
    fun incrementViewCount() {
        this.viewCount++
    }

    /**
     * 판매량 및 판매 금액 증가
     */
    fun incrementSales(quantity: Long, amount: Long) {
        this.salesCount += quantity
        this.totalSalesAmount += amount
    }

    /**
     * 판매량 및 판매 금액 감소 (주문 취소 시)
     */
    fun decrementSales(quantity: Long, amount: Long) {
        if (this.salesCount >= quantity) {
            this.salesCount -= quantity
        }
        if (this.totalSalesAmount >= amount) {
            this.totalSalesAmount -= amount
        }
    }

    companion object {
        /**
         * 새로운 메트릭 생성
         */
        fun create(productId: Long): ProductMetrics {
            return ProductMetrics(productId = productId)
        }
    }
}
