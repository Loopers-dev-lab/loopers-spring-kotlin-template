package com.loopers.domain.order

import com.loopers.domain.point.PointRepository
import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.product.Stock
import com.loopers.domain.product.StockRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class OrderService(
    private val orderRepository: OrderRepository,
    private val productRepository: ProductRepository,
    private val stockRepository: StockRepository,
    private val pointRepository: PointRepository,
) {
    @Transactional
    fun createOrder(userId: Long, commands: List<CreateOrderItemCommand>): Order {
        val orderItems = validateAndCreateOrderItems(commands)
        val order = Order(userId = userId, items = orderItems)
        val totalAmount = order.calculateTotalAmount()

        validateUserPoint(userId, totalAmount)

        val savedOrder = orderRepository.save(order)

        deductStocks(commands)
        deductUserPoint(userId, totalAmount)

        return savedOrder
    }

    private fun validateAndCreateOrderItems(
        commands: List<CreateOrderItemCommand>,
    ): List<OrderItem> = commands.map { command ->
        val product = productRepository.findById(command.productId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다: ${command.productId}")

        val stock = stockRepository.findByProductId(command.productId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "재고 정보를 찾을 수 없습니다: ${command.productId}")

        validateStockAvailability(stock, product.name, command.quantity)

        createOrderItemSnapshot(product, command.quantity)
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

    private fun deductStocks(commands: List<CreateOrderItemCommand>) {
        commands.sortedBy { it.productId }.forEach { command ->
            val stock = stockRepository.findByProductIdWithLock(command.productId)
                ?: throw CoreException(ErrorType.NOT_FOUND, "재고 정보를 찾을 수 없습니다: ${command.productId}")
            stock.decrease(command.quantity)
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
