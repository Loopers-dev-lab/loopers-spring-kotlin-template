package com.loopers.domain.product

import com.loopers.domain.order.OrderCommand
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

object OrderValidator {

    fun validate(
        items: List<OrderCommand.OrderDetailCommand>,
        products: List<Product>,
        stocks: List<Stock>,
    ) {
        validateProductsExist(items, products)
        validateStockAvailability(items, products, stocks)
    }

    /**
     * 요청된 모든 상품이 실제로 존재하는지 검증합니다.
     *
     * @param items 주문하려는 상품 목록
     * @param products 실제 조회된 상품 목록
     * @throws CoreException 존재하지 않는 상품 ID가 있는 경우 (ErrorType.NOT_FOUND)
     */
    private fun validateProductsExist(
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

    /**
     * 각 상품의 재고가 주문 수량을 충족하는지 검증합니다.
     *
     * @param items 주문하려는 상품 목록
     * @param products 상품 엔티티 목록
     * @throws CoreException 재고 정보가 없거나 재고가 부족한 경우
     */
    private fun validateStockAvailability(
        items: List<OrderCommand.OrderDetailCommand>,
        products: List<Product>,
        stocks: List<Stock>,
    ) {
        val stockMap = stocks.associateBy { it.productId }

        items
            .groupBy { it.productId }
            .forEach { (productId, groupedItems) ->
                val stock = getStockOrThrow(productId, stockMap)
                val requestedQuantity = groupedItems.sumOf { it.quantity.toLong() }
                validateSufficientStock(productId, requestedQuantity, stock, products)
            }
    }

    /**
     * 상품 ID에 해당하는 재고 정보를 조회합니다.
     *
     * @param productId 상품 ID
     * @param stockMap 상품 ID를 키로 하는 재고 맵
     * @return 재고 엔티티
     * @throws CoreException 재고 정보가 존재하지 않는 경우 (ErrorType.NOT_FOUND)
     */
    private fun getStockOrThrow(
        productId: Long,
        stockMap: Map<Long, Stock>,
    ): Stock {
        return stockMap[productId]
            ?: throw CoreException(
                ErrorType.NOT_FOUND,
                "재고 정보를 찾을 수 없습니다: $productId",
            )
    }

    /**
     * 특정 상품의 재고가 요청 수량을 충족하는지 검증합니다.
     *
     * @param item 주문 항목 (상품 ID와 수량)
     * @param stock 재고 엔티티
     * @param products 상품 엔티티 목록
     * @throws CoreException 재고가 부족한 경우 (ErrorType.INSUFFICIENT_STOCK)
     */
    private fun validateSufficientStock(
        productId: Long,
        requestedQuantity: Long,
        stock: Stock,
        products: List<Product>,
    ) {
        if (!stock.isAvailable(requestedQuantity)) {
            val productName = products.find { it.id == productId }?.name ?: "알 수 없음"
            throw CoreException(
                ErrorType.INSUFFICIENT_STOCK,
                "상품 '$productName'의 재고가 부족합니다. (요청: $requestedQuantity, 현재: ${stock.quantity})",
            )
        }
    }
}
