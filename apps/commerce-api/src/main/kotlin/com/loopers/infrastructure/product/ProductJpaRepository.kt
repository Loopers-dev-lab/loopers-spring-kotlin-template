package com.loopers.infrastructure.product

import com.loopers.domain.product.Product
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface ProductJpaRepository : JpaRepository<Product, Long> {
    fun findAllByBrandId(brandId: Long, pageable: Pageable): Page<Product>

    @Query(
        value = """
        SELECT p
        FROM ProductLikeCount plc
        JOIN Product p ON p.id = plc.productId
        WHERE p.brandId = :brandId
        ORDER BY plc.likeCount DESC
    """,
        countQuery = """
        SELECT COUNT(DISTINCT p.id) FROM Product p 
        WHERE p.brandId = :brandId
    """,
    )
    fun findAllByBrandIdOrderByLikesDesc(
        brandId: Long,
        pageable: Pageable,
    ): Page<Product>

    @Query(
        value = """
        SELECT p
        FROM ProductLikeCount plc
        JOIN Product p ON p.id = plc.productId
        ORDER BY plc.likeCount DESC
    """,
        countQuery = """
        SELECT COUNT(p) FROM Product p
    """,
    )
    fun findAllOrderByLikesDesc(
        pageable: Pageable,
    ): Page<Product>
}
