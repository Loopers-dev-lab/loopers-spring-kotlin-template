package com.loopers.infrastructure.product

import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

@Component
class ProductRepositoryImpl(
    private val productJpaRepository: ProductJpaRepository,
) : ProductRepository {
    override fun findById(id: Long): Product? = productJpaRepository.findByIdOrNull(id)

    override fun findAll(
        brandId: Long?,
        sort: String,
        pageable: Pageable,
    ): Page<Product> = when {
        brandId != null && sort == "likeCount" -> {
            productJpaRepository.findByBrandIdOrderByLikeCount(brandId, pageable)
        }
        brandId != null && sort == "price" -> {
            productJpaRepository.findByBrandId(brandId, pageable.withSort(Sort.by("price.amount").ascending()))
        }
        brandId != null -> {
            productJpaRepository.findByBrandId(brandId, pageable)
        }
        sort == "likeCount" -> {
            productJpaRepository.findAllOrderByLikeCount(pageable)
        }
        sort == "price" -> {
            productJpaRepository.findAll(pageable.withSort(Sort.by("price.amount").ascending()))
        }
        else -> {
            productJpaRepository.findAll(pageable)
        }
    }

    override fun save(product: Product): Product = productJpaRepository.save(product)

    override fun existsById(id: Long): Boolean = productJpaRepository.existsById(id)

    private fun Pageable.withSort(
        sort: Sort,
    ): Pageable = org.springframework.data.domain.PageRequest.of(
        this.pageNumber,
        this.pageSize,
        sort,
    )
}
