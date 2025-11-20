package com.loopers.domain.product

import com.loopers.application.order.OrderCommand
import com.loopers.application.product.ProductInfo
import com.loopers.domain.product.stock.StockRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class ProductService(private val productRepository: ProductRepository, private val stockRepository: StockRepository) {

    fun getProducts(sort: String, direction: String, page: Int, size: Int): Page<ProductInfo> {
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.fromString(direction), sort))

        val convertedPageable = if (pageable.sort.isSorted) {
            val convertedSort = pageable.sort.map { order ->
                when (order.property) {
                    "likeCount" -> Sort.Order(order.direction, "pts.likeCount")
                    else -> order
                }
            }.let { Sort.by(it.toList()) }
            PageRequest.of(pageable.pageNumber, pageable.pageSize, convertedSort)
        } else {
            pageable
        }

        return productRepository.findAllProductInfos(convertedPageable)
    }

    @Transactional
    fun occupyStocks(command: OrderCommand) {
        command.orderItems.forEach {
            val stock = stockRepository.getStockByProductIdWithPessimisticLock(it.productId)
            stock.occupy(it.quantity)

            stockRepository.save(stock)
        }
    }
}
