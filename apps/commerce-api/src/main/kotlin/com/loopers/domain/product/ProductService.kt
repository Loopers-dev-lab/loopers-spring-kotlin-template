package com.loopers.domain.product

import com.loopers.domain.order.OrderCommand
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service

@Service
class ProductService(
    private val productRepository: ProductRepository,
) {

    fun getProducts(brandId: Long?, sort: ProductSort, pageable: Pageable): Page<Product> {
        return productRepository.findAll(brandId, sort, pageable)
    }

    fun getProduct(productId: Long): Product? {
        return productRepository.findBy(productId)
    }

    fun getProducts(productIds: List<Long>): List<Product> {
        return productRepository.findAllBy(productIds)
    }

    fun getStocksBy(productIds: List<Long>): List<Stock> {
        return productRepository.findStockAllBy(productIds)
    }

    fun deductAllStock(
        items: List<OrderCommand.OrderDetailCommand>,
        stocks: List<Stock>,
    ) {
        val stockMap = stocks.associateBy { it.productId }

        items.forEach { item ->
            val stock = stockMap[item.productId]!!
            stock.decrease(item.quantity)
        }
    }
}
