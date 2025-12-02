package com.loopers.infrastructure.product

import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.product.ProductSortType
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Component

@Component
class ProductRepositoryImpl(
    private val productJpaRepository: ProductJpaRepository,
) : ProductRepository {
    override fun save(product: Product): Product {
        return productJpaRepository.save(product)
    }

    override fun findById(id: Long): Product? {
        return productJpaRepository.findById(id).orElse(null)
    }

    override fun findByIdOrThrow(id: Long): Product {
        return findById(id)
            ?: throw CoreException(ErrorType.PRODUCT_NOT_FOUND,"상품을 찾을 수 없습니다. id: $id")
    }
    override fun findAll(
        brandId: Long?,
        sort: ProductSortType,
        pageable: Pageable,
    ): Page<Product> {
        val pageableWithSort = createPageableWithSort(sort, pageable)

        return if (brandId != null) {
            productJpaRepository.findByBrandId(brandId, pageableWithSort)
        } else {
            productJpaRepository.findAll(pageableWithSort)
        }
    }

    override fun count(brandId: Long?): Long {
        return if (brandId != null) {
            productJpaRepository.countByBrandId(brandId)
        } else {
            productJpaRepository.count()
        }
    }

    private fun createPageableWithSort(sort: ProductSortType, pageable: Pageable): Pageable {
        val sortOrder = when(sort) {
            ProductSortType.LATEST -> Sort.by(Sort.Direction.DESC, "createdAt")
            ProductSortType.PRICE_ASC -> Sort.by(Sort.Direction.ASC, "price.amount")
            ProductSortType.LIKES_DESC -> Sort.by(Sort.Direction.DESC, "likesCount")
        }
        return PageRequest.of(pageable.pageNumber, pageable.pageSize, sortOrder)
    }

    override fun findAllByIdIn(ids: List<Long>): List<Product> {
        return productJpaRepository.findAllById(ids)
    }

    override fun findByIdWithLock(id: Long): Product? {
        return productJpaRepository.findByIdWithLock(id)
    }

    override fun findByIdWithLockOrThrow(id: Long): Product {
        return findByIdWithLock(id)
            ?: throw CoreException(ErrorType.PRODUCT_NOT_FOUND, "상품을 찾을 수 없습니다. id: $id")
    }

    override fun findAllByIdInWithLock(ids: List<Long>): List<Product> {
        return productJpaRepository.findAllByIdInWithLock(ids)
    }
}
