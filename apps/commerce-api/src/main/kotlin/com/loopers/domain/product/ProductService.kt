package com.loopers.domain.product

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.data.domain.Slice
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class ProductService(
    private val productRepository: ProductRepository,
    private val stockRepository: StockRepository,
    private val productStatisticRepository: ProductStatisticRepository,
    private val brandRepository: BrandRepository,
) {
    @Transactional(readOnly = true)
    fun findProductById(id: Long): Product {
        return productRepository.findById(id)
            ?: throw CoreException(
                errorType = ErrorType.NOT_FOUND,
                customMessage = "[id = $id] 상품을 찾을 수 없습니다.",
            )
    }

    @Transactional(readOnly = true)
    fun findProductViewById(id: Long): ProductView {
        val product = productRepository.findById(id)
            ?: throw CoreException(
                errorType = ErrorType.NOT_FOUND,
                customMessage = "[id = $id] 상품을 찾을 수 없습니다.",
            )

        val stock = stockRepository.findByProductId(id)
            ?: throw CoreException(
                errorType = ErrorType.INTERNAL_ERROR,
                customMessage = "[productId = $id] 상품의 재고를 찾을 수 없습니다.",
            )

        val brand = brandRepository.findById(product.brandId)
            ?: throw CoreException(
                errorType = ErrorType.INTERNAL_ERROR,
                customMessage = "[brandId = ${product.brandId}] 상품의 브랜드를 찾을 수 없습니다.",
            )

        val statistic = productStatisticRepository.findByProductId(id)
            ?: throw CoreException(
                errorType = ErrorType.INTERNAL_ERROR,
                customMessage = "[productId = $id] 상품의 통계를 찾을 수 없습니다.",
            )

        return ProductView(
            product = product,
            stock = stock,
            statistic = statistic,
            brand = brand,
        )
    }

    @Transactional(readOnly = true)
    fun findProducts(command: ProductCommand.FindProducts): Slice<ProductView> {
        val pageQuery = command.to()
        val slicedProduct = productRepository.findAllBy(pageQuery)

        val slicedProductIds = slicedProduct.content.map { it.id }

        val stocksToMap = stockRepository
            .findAllByProductIds(slicedProductIds)
            .associateBy { it.productId }

        val productStatisticsToMap = productStatisticRepository
            .findAllByProductIds(slicedProductIds)
            .associateBy { it.productId }

        val slicedProductBrandIds = slicedProduct.content.map { it.brandId }
        val brandsToMap = brandRepository
            .findAllByIds(slicedProductBrandIds)
            .associateBy { it.id }

        return slicedProduct.map {
            ProductView(
                product = it,
                stock = stocksToMap[it.id]
                    ?: throw CoreException(
                        errorType = ErrorType.INTERNAL_ERROR,
                        customMessage = "[productId = ${it.id}] 상품의 재고를 찾을 수 없습니다.",
                    ),
                statistic = productStatisticsToMap[it.id]
                    ?: throw CoreException(
                        errorType = ErrorType.INTERNAL_ERROR,
                        customMessage = "[productId = ${it.id}] 상품의 통계를 찾을 수 없습니다.",
                    ),
                brand = brandsToMap[it.brandId]
                    ?: throw CoreException(
                        errorType = ErrorType.INTERNAL_ERROR,
                        customMessage = "[brandId = ${it.brandId}] 상품의 브랜드를 찾을 수 없습니다.",
                    ),
            )
        }
    }

    @Transactional(readOnly = true)
    fun findAllByIds(ids: List<Long>): List<Product> {
        val products = productRepository.findAllByIds(ids)

        // 요청한 ID와 조회된 ID 비교
        val foundIds = products.map { it.id }.toSet()
        val notFoundIds = ids.filterNot { it in foundIds }

        if (notFoundIds.isNotEmpty()) {
            throw CoreException(
                ErrorType.NOT_FOUND,
                "존재하지 않는 상품입니다: ${notFoundIds.joinToString()}",
            )
        }

        return products
    }

    @Transactional(readOnly = true)
    fun findAllProductViewByIds(ids: List<Long>): List<ProductView> {
        if (ids.isEmpty()) return emptyList()

        val products = productRepository.findAllByIds(ids)

        // 요청한 ID와 조회된 ID 비교
        val foundIds = products.map { it.id }.toSet()
        val notFoundIds = ids.filterNot { it in foundIds }

        if (notFoundIds.isNotEmpty()) {
            throw CoreException(
                ErrorType.NOT_FOUND,
                "존재하지 않는 상품입니다: ${notFoundIds.joinToString()}",
            )
        }

        val stocksToMap = stockRepository
            .findAllByProductIds(ids)
            .associateBy { it.productId }

        val productStatisticsToMap = productStatisticRepository
            .findAllByProductIds(ids)
            .associateBy { it.productId }

        val brandIds = products.map { it.brandId }.distinct()
        val brandsToMap = brandRepository
            .findAllByIds(brandIds)
            .associateBy { it.id }

        return products.map { product ->
            ProductView(
                product = product,
                stock = stocksToMap[product.id]
                    ?: throw CoreException(
                        errorType = ErrorType.INTERNAL_ERROR,
                        customMessage = "[productId = ${product.id}] 상품의 재고를 찾을 수 없습니다.",
                    ),
                statistic = productStatisticsToMap[product.id]
                    ?: throw CoreException(
                        errorType = ErrorType.INTERNAL_ERROR,
                        customMessage = "[productId = ${product.id}] 상품의 통계를 찾을 수 없습니다.",
                    ),
                brand = brandsToMap[product.brandId]
                    ?: throw CoreException(
                        errorType = ErrorType.INTERNAL_ERROR,
                        customMessage = "[brandId = ${product.brandId}] 상품의 브랜드를 찾을 수 없습니다.",
                    ),
            )
        }
    }

    @Transactional
    fun decreaseStocks(command: ProductCommand.DecreaseStocks) {
        val decreaseStockMap = command.units.associateBy { it.productId }

        val decreaseProductIds = decreaseStockMap.keys.toList()
        val lockedStocks = stockRepository.findAllByProductIdsWithLock(decreaseProductIds)

        // 요청한 상품 ID에 대한 재고가 모두 존재하는지 확인
        val foundProductIds = lockedStocks.map { it.productId }.toSet()
        val notFoundProductIds = decreaseProductIds.filterNot { it in foundProductIds }
        if (notFoundProductIds.isNotEmpty()) {
            throw CoreException(
                errorType = ErrorType.INTERNAL_ERROR,
                customMessage = "재고를 찾을 수 없는 상품입니다: ${notFoundProductIds.joinToString()}",
            )
        }

        lockedStocks.forEach { stock ->
            val decreasedStock = decreaseStockMap[stock.productId]
                ?: throw CoreException(
                    errorType = ErrorType.BAD_REQUEST,
                    customMessage = "[productId = ${stock.productId}] 상품의 재고를 감소할 수 없습니다.",
                )

            stock.decrease(decreasedStock.amount)
        }

        stockRepository.saveAll(lockedStocks)
    }

    @Transactional
    fun increaseProductLikeCount(productId: Long) {
        productStatisticRepository.increaseLikeCountBy(productId)
    }

    @Transactional
    fun decreaseProductLikeCount(productId: Long) {
        productStatisticRepository.decreaseLikeCountBy(productId)
    }

    /**
     * 재고를 복구합니다. 결제 실패 시 감소했던 재고를 되돌립니다.
     *
     * @param command 복구할 재고 정보 (productId, amount)
     */
    @Transactional
    fun increaseStocks(command: ProductCommand.IncreaseStocks) {
        val increaseStockMap = command.units.associateBy { it.productId }

        val increaseProductIds = increaseStockMap.keys.toList()
        val lockedStocks = stockRepository.findAllByProductIdsWithLock(increaseProductIds)

        // 요청한 상품 ID에 대한 재고가 모두 존재하는지 확인
        val foundProductIds = lockedStocks.map { it.productId }.toSet()
        val notFoundProductIds = increaseProductIds.filterNot { it in foundProductIds }
        if (notFoundProductIds.isNotEmpty()) {
            throw CoreException(
                errorType = ErrorType.INTERNAL_ERROR,
                customMessage = "재고를 찾을 수 없는 상품입니다: ${notFoundProductIds.joinToString()}",
            )
        }

        lockedStocks.forEach { stock ->
            val increasedStock = increaseStockMap[stock.productId]
                ?: throw CoreException(
                    errorType = ErrorType.BAD_REQUEST,
                    customMessage = "[productId = ${stock.productId}] 상품의 재고를 증가할 수 없습니다.",
                )

            stock.increase(increasedStock.amount)
        }

        stockRepository.saveAll(lockedStocks)
    }
}
