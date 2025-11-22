package com.loopers.infrastructure.product

import com.loopers.domain.product.Product
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface ProductJpaRepository : JpaRepository<Product, Long> {
    fun findByBrandId(brandId: Long, pageable: Pageable): Page<Product>

    fun findByIdInAndDeletedAtIsNull(ids: List<Long>): List<Product>

    @Query(
        """
        SELECT p FROM Product p
        ORDER BY p.likeCount DESC, p.id DESC
    """,
    countQuery = """
        SELECT COUNT(p) FROM Product p
    """,
    )
    fun findAllOrderByLikeCount(pageable: Pageable): Page<Product>

    @Query(
        """
        SELECT p FROM Product p
        WHERE p.brand.id = :brandId
        ORDER BY p.likeCount DESC, p.id DESC
    """,
    countQuery = """
        SELECT COUNT(p) FROM Product p
        WHERE p.brand.id = :brandId
    """,
    )
    fun findByBrandIdOrderByLikeCount(brandId: Long, pageable: Pageable): Page<Product>
}
