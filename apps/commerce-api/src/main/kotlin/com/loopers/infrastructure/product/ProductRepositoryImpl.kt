package com.loopers.infrastructure.product

import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.product.ProductSort
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
class ProductRepositoryImpl(
    private val productJpaRepository: ProductJpaRepository,
) : ProductRepository {
    override fun findBy(id: Long): Product? {
        return productJpaRepository.findByIdOrNull(id)
    }

    override fun findAllBy(ids: List<Long>): List<Product> {
        return productJpaRepository.findAllById(ids)
    }

    override fun findAll(
        brandId: Long?,
        sort: ProductSort,
        pageable: Pageable,
    ): Page<Product> {
        return when (sort) {
            ProductSort.LIKE_DESC -> {
                brandId?.let {
                    productJpaRepository.findAllByBrandIdOrderByLikesDesc(it, pageable)
                } ?: productJpaRepository.findAllOrderByLikesDesc(pageable)
            }

            ProductSort.LATEST -> {
                val sortedPageable = sortPageable(pageable, Sort.by("createdAt").descending())
                brandId?.let { productJpaRepository.findAllByBrandId(it, sortedPageable) }
                    ?: productJpaRepository.findAll(sortedPageable)
            }

            ProductSort.PRICE_ASC -> {
                val sortedPageable = sortPageable(pageable, Sort.by("price").ascending())
                brandId?.let { productJpaRepository.findAllByBrandId(it, sortedPageable) }
                    ?: productJpaRepository.findAll(sortedPageable)
            }
        }
    }

    private fun sortPageable(pageable: Pageable, sort: Sort): Pageable {
        return PageRequest.of(pageable.pageNumber, pageable.pageSize, sort)
    }
}
