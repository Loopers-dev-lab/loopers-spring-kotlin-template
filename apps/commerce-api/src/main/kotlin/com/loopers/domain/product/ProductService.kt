package com.loopers.domain.product

import com.loopers.domain.order.OrderCommand
import com.loopers.domain.order.OrderDetail
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ProductService(
    private val productRepository: ProductRepository,
) {

    @Transactional(readOnly = true)
    fun getProducts(brandId: Long?, sort: ProductSort, pageable: Pageable): Page<Product> {
        return productRepository.findAll(brandId, sort, pageable)
    }

    @Transactional(readOnly = true)
    fun getProduct(productId: Long): Product? {
        return productRepository.findBy(productId)
    }

    @Transactional(readOnly = true)
    fun getProducts(productIds: List<Long>): List<Product> {
        return productRepository.findAllBy(productIds)
    }

    @Transactional
    fun getStocksBy(productIds: List<Long>): List<Stock> {
        return productRepository.findStockAllByWithLock(productIds)
    }

    fun validateProductsExist(
        items: List<OrderCommand.OrderDetailCommand>,
        products: List<Product>,
    ) {
        val requestedIds = items.map { it.productId }
        val foundIds = products.map { it.id }.toSet()
        val missingIds = requestedIds.filterNot { it in foundIds }

        if (missingIds.isNotEmpty()) {
            throw CoreException(
                ErrorType.NOT_FOUND,
                "상품을 찾을 수 없습니다: ${missingIds.joinToString(", ")}",
            )
        }
    }

    @Transactional(readOnly = true)
    fun validateStockAvailability(items: List<OrderCommand.OrderDetailCommand>) {
        val productIds = items.map { it.productId }.distinct().sorted()
        val stocks = productRepository.findStockAllBy(productIds)
        val stockMap = stocks.associateBy { it.productId }

        items
            .groupBy { it.productId }
            .forEach { (productId, groupedItems) ->
                val stock = stockMap[productId]
                    ?: throw CoreException(
                        ErrorType.NOT_FOUND,
                        "재고 정보를 찾을 수 없습니다: $productId",
                    )

                val requestedQuantity = groupedItems.sumOf { it.quantity }
                if (stock.quantity < requestedQuantity) {
                    throw CoreException(
                        ErrorType.INSUFFICIENT_STOCK,
                        "재고가 부족합니다. 상품 ID: $productId, 요청: $requestedQuantity, 재고: ${stock.quantity}",
                    )
                }
            }
    }

    @Transactional
    fun deductAllStock(
        orderDetails: List<OrderDetail>,
    ) {
        val productIds = orderDetails.map { it.productId }
            .distinct()
            .sorted()
        val stocks = productRepository.findStockAllByWithLock(productIds)

        val stockMap = stocks.associateBy { it.productId }

        orderDetails
            .groupBy { it.productId }
            .forEach { (productId, groupedDetails) ->
                val stock = stockMap[productId]
                    ?: throw CoreException(
                        ErrorType.NOT_FOUND,
                        "재고 정보를 찾을 수 없습니다: $productId",
                    )

                val totalQuantity = groupedDetails.sumOf { it.quantity }

                stock.decrease(totalQuantity)
            }
    }
}
