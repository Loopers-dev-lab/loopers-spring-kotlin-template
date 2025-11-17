package com.loopers.domain.product

import com.loopers.application.order.OrderCommand
import com.loopers.application.product.ProductInfo
import jakarta.transaction.Transactional
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Component

@Component
class ProductService(private val productRepository: ProductRepository) {

    fun getProducts(sort: String, direction: String, page: Int, size: Int): Page<ProductInfo> {
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.fromString(direction), sort))

        val convertedPageable = if (pageable.sort.isSorted) {
            val convertedSort = pageable.sort.map { order ->
                when (order.property) {
                    "likeCount" -> Sort.Order(order.direction, "s.likeCount")
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
            val product = productRepository.getProductBy(it.productId)
            product.occupyStock(it.quantity)
        }
    }
}
