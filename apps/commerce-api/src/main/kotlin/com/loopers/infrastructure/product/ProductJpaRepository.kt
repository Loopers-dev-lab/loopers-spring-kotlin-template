package com.loopers.infrastructure.product

import com.loopers.domain.product.Product
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface ProductJpaRepository : JpaRepository<Product, Long> {
    fun findByBrandId(brandId: Long, pageable: Pageable): Page<Product>

    @Query(
        value = """
        SELECT p FROM Product p 
        LEFT JOIN ProductLike pl ON p.id = pl.productId
        WHERE p.brandId = :brandId
        GROUP BY p.id
        ORDER BY COUNT(pl.id) DESC
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
        SELECT p FROM Product p 
        LEFT JOIN ProductLike pl ON p.id = pl.productId
        GROUP BY p.id
        ORDER BY COUNT(pl.id) DESC
    """,
        countQuery = """
        SELECT COUNT(p) FROM Product p
    """,
    )
    fun findAllOrderByLikesDesc(
        pageable: Pageable,
    ): Page<Product>
}
