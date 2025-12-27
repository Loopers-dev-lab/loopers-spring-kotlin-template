package com.loopers.infrastructure.product

import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.product.SortType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
class ProductRepositoryImpl(private val productJpaRepository: ProductJpaRepository) : ProductRepository {
    override fun findById(id: Long): Product? = productJpaRepository.findByIdOrNull(id)

    override fun findAllById(ids: List<Long>): List<Product> = productJpaRepository.findAllById(ids)

    override fun findByIdInAndDeletedAtIsNull(ids: List<Long>): List<Product> =
        productJpaRepository.findByIdInAndDeletedAtIsNull(ids)

    override fun findAll(
        brandId: Long?,
        sort: SortType,
        pageable: Pageable,
    ): Page<Product> = when (sort) {
        SortType.LIKES_DESC -> {
            if (brandId != null) {
                productJpaRepository.findByBrandIdOrderByLikeCount(brandId, pageable)
            } else {
                productJpaRepository.findAllOrderByLikeCount(pageable)
            }
        }

        SortType.PRICE_ASC -> {
            if (brandId != null) {
                productJpaRepository.findByBrandId(brandId, pageable.withSort(Sort.by("price.amount").ascending()))
            } else {
                productJpaRepository.findAll(pageable.withSort(Sort.by("price.amount").ascending()))
            }
        }

        SortType.LATEST -> {
            val sortedPageable = pageable.withSort(Sort.by(Sort.Order.desc("createdAt")))
            if (brandId != null) {
                productJpaRepository.findByBrandId(brandId, sortedPageable)
            } else {
                productJpaRepository.findAll(sortedPageable)
            }
        }
    }

    override fun save(product: Product): Product = productJpaRepository.save(product)
    override fun saveAll(products: List<Product>): List<Product> = productJpaRepository.saveAll(products)

    override fun existsById(id: Long): Boolean = productJpaRepository.existsById(id)

    override fun findByIdWithLock(id: Long): Product? = productJpaRepository.findByIdWithLock(id)

    private fun Pageable.withSort(
        sort: Sort,
    ): Pageable = org.springframework.data.domain.PageRequest.of(
        this.pageNumber,
        this.pageSize,
        sort,
    )
}
