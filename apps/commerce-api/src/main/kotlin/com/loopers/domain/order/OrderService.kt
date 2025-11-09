package com.loopers.domain.order

import com.loopers.domain.point.PointRepository
import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.product.Stock
import com.loopers.domain.product.StockRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service

data class OrderItemRequest(
    val productId: Long,
    val quantity: Int,
)

@Service
class OrderService(
    private val orderRepository: OrderRepository,
    private val productRepository: ProductRepository,
    private val stockRepository: StockRepository,
    private val pointRepository: PointRepository,
) {
    @Transactional
    fun createOrder(userId: Long, orderItemRequests: List<OrderItemRequest>): Order {
        val orderItems = validateAndCreateOrderItems(orderItemRequests)
        val order = Order(userId = userId, items = orderItems)
        val totalAmount = order.calculateTotalAmount()

        validateUserPoint(userId, totalAmount)

        val savedOrder = orderRepository.save(order)

        deductStocks(orderItemRequests)
        deductUserPoint(userId, totalAmount)

        return savedOrder
    }

    private fun validateAndCreateOrderItems(
        orderItemRequests: List<OrderItemRequest>,
    ): List<OrderItem> = orderItemRequests.map { request ->
        val product = productRepository.findById(request.productId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다: ${request.productId}")

        val stock = stockRepository.findByProductId(request.productId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "재고 정보를 찾을 수 없습니다: ${request.productId}")

        validateStockAvailability(stock, product.name, request.quantity)

        createOrderItemSnapshot(product, request.quantity)
    }

    private fun validateStockAvailability(stock: Stock, productName: String, requestedQuantity: Int) {
        if (!stock.isAvailable(requestedQuantity)) {
            throw CoreException(
                ErrorType.BAD_REQUEST,
                "재고 부족: 상품 $productName, 현재 재고 ${stock.quantity}, 요청 수량 $requestedQuantity",
            )
        }
    }

    private fun createOrderItemSnapshot(
        product: Product,
        quantity: Int,
    ): OrderItem = OrderItem(
        productId = product.id,
        productName = product.name,
        brandId = product.brand.id,
        brandName = product.brand.name,
        brandDescription = product.brand.description,
        quantity = quantity,
        priceAtOrder = product.price,
    )

    private fun validateUserPoint(userId: Long, totalAmount: Money) {
        val point = pointRepository.findByUserId(userId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "포인트 정보를 찾을 수 없습니다: $userId")

        if (!point.canDeduct(totalAmount)) {
            throw CoreException(
                ErrorType.BAD_REQUEST,
                "포인트 부족: 현재 잔액 ${point.balance.amount}, 필요 금액 ${totalAmount.amount}",
            )
        }
    }

    private fun deductStocks(orderItemRequests: List<OrderItemRequest>) {
        orderItemRequests.forEach { request ->
            val stock = stockRepository.findByProductIdWithLock(request.productId)
                ?: throw CoreException(ErrorType.NOT_FOUND, "재고 정보를 찾을 수 없습니다: ${request.productId}")
            stock.decrease(request.quantity)
            stockRepository.save(stock)
        }
    }

    private fun deductUserPoint(userId: Long, totalAmount: Money) {
        val lockedPoint = pointRepository.findByUserIdWithLock(userId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "포인트 정보를 찾을 수 없습니다: $userId")
        lockedPoint.deduct(totalAmount)
        pointRepository.save(lockedPoint)
    }
}
