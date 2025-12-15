package com.loopers.domain.product

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

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
            columnNames = ["product_id"],
        ),
    ],
    indexes = [
        Index(name = "idx_product_metrics_like_count", columnList = "like_count DESC"),
        Index(name = "idx_product_metrics_view_count", columnList = "view_count DESC"),
        Index(name = "idx_product_metrics_sales_count", columnList = "sales_count DESC"),
    ],
)
class ProductMetrics(
    /**
     * 상품 ID
     */
    @Column(name = "product_id", nullable = false, unique = true)
    val productId: Long,

    /**
     * 좋아요 수
     */
    @Column(name = "like_count", nullable = false)
    var likeCount: Long = 0,

    /**
     * 조회 수 (상세 페이지 조회)
     */
    @Column(name = "view_count", nullable = false)
    var viewCount: Long = 0,

    /**
     * 판매량 (주문 완료 기준)
     */
    @Column(name = "sales_count", nullable = false)
    var salesCount: Long = 0,

    /**
     * 총 판매 금액
     */
    @Column(name = "total_sales_amount", nullable = false)
    var totalSalesAmount: Long = 0,
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
        require(quantity > 0 && amount > 0) {
            "판매량과 판매 금액은 양수여야 합니다: quantity=$quantity, amount=$amount"
        }
        this.salesCount += quantity
        this.totalSalesAmount += amount
    }

    /**
     * 판매량 및 판매 금액 감소 (주문 취소 시)
     */
    fun decrementSales(quantity: Long, amount: Long) {
        // 두 값을 함께 검증하여 일관성 보장
        require(this.salesCount >= quantity && this.totalSalesAmount >= amount) {
            "판매량 또는 판매 금액이 부족합니다: " +
                    "현재 판매량=$salesCount, 요청 감소량=$quantity, " +
                    "현재 판매 금액=$totalSalesAmount, 요청 감소 금액=$amount"
        }

        // 검증 통과 후 원자적으로 감소
        this.salesCount -= quantity
        this.totalSalesAmount -= amount
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
