package com.loopers.domain.product

import com.loopers.domain.order.OrderCommand
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

    @Transactional(readOnly = true)
    fun getStocksBy(productIds: List<Long>): List<Stock> {
        return productRepository.findStockAllBy(productIds)
    }

    @Transactional
    fun deductAllStock(
        items: List<OrderCommand.OrderDetailCommand>,
        stocks: List<Stock>,
    ) {
        val stockMap = stocks.associateBy { it.productId }

        items.forEach { item ->
            val stock = stockMap[item.productId]
                ?: throw CoreException(ErrorType.INSUFFICIENT_STOCK, "productId=${item.productId}에 대한 재고가 없습니다.")
            stock.decrease(item.quantity)
        }
    }
}
