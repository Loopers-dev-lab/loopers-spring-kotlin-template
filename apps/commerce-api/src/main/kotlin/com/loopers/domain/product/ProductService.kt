package com.loopers.domain.product

import com.loopers.domain.order.Order
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class ProductService(
    private val productRepository: ProductRepository,
) {
    @Transactional(readOnly = true)
    fun getProduct(productId: Long): Product {
        return productRepository.findByIdOrThrow(productId)
    }

    @Transactional(readOnly = true)
    fun getProducts(
        brandId: Long?,
        sort: ProductSortType,
        pageable: Pageable,
    ): Page<Product> {
        return productRepository.findAll(brandId, sort, pageable)
    }

    @Transactional(readOnly = true)
    fun getProductsByIds(productIds: List<Long>): List<Product> {
        return productRepository.findAllByIdIn(productIds)
    }

    @Transactional
    fun decreaseStockByOrder(order: Order) {
        val productIds = order.items.map { it.productId }
        val productMap = productRepository.findAllByIdInWithLock(productIds)
            .associateBy { it.id }

        order.items.forEach { item ->
            val product = productMap[item.productId]
                ?: throw CoreException(
                    ErrorType.PRODUCT_NOT_FOUND,
                    "상품을 찾을 수 없습니다. id: ${item.productId}"
                )
            product.decreaseStock(item.quantity)
        }
    }
}
