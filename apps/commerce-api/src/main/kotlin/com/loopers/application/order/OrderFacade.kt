package com.loopers.application.order

import com.loopers.domain.brand.BrandService
import com.loopers.domain.order.OrderCommand
import com.loopers.domain.order.OrderCommand.OrderDetailCommand
import com.loopers.domain.order.OrderService
import com.loopers.domain.order.OrderTotalAmountCalculator
import com.loopers.domain.point.PointService
import com.loopers.domain.product.ProductService
import com.loopers.domain.user.UserService
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class OrderFacade(
    private val orderService: OrderService,
    private val userService: UserService,
    private val brandService: BrandService,
    private val productService: ProductService,
    private val pointService: PointService,
) {

    @Transactional(readOnly = true)
    fun getOrders(userId: String, pageable: Pageable): Page<OrderResult.ListInfo> {
        val user = userService.getMyInfo(userId)

        val orderPage = orderService.getOrders(user.id, pageable)

        return orderPage.map { OrderResult.ListInfo.from(it) }
    }

    @Transactional(readOnly = true)
    fun getOrder(userId: String, orderId: Long): OrderResult.DetailInfo {
        val user = userService.getMyInfo(userId)

        val order = orderService.getOrder(user.id, orderId)
        val orderDetail = orderService.getOrderDetail(orderId)

        return OrderResult.DetailInfo.from(order, orderDetail)
    }

    @Transactional
    fun createOrder(userId: String, items: List<OrderDetailCommand>) {
        val user = userService.getMyInfo(userId)

        // 1. 상품 조회
        val productIds = items.map { it.productId }
        val products = productService.getProducts(productIds)

        // 2. 상품 존재 여부 검증
        productService.validateProductsExist(items, products)

        // 3. 브랜드 조회
        val brandIds = products.map { it.brandId }.distinct()
        val brands = brandService.getAllBrand(brandIds)

        // 4. 총 주문 금액 계산
        val totalAmount = OrderTotalAmountCalculator.calculate(items, products)

        // 5. 포인트 사용
        pointService.use(
            userId = user.id,
            amount = totalAmount
        )

        // 6. 재고 감소
        productService.deductAllStock(items)

        // 7. 주문 생성
        orderService.createOrder(
            OrderCommand.Create(
                userId = user.id,
                totalAmount = totalAmount,
                items = items,
                brands = brands,
                products = products,
            ),
        )
        // TODO. 주문 정보 외부 시스템 전송
    }
}
