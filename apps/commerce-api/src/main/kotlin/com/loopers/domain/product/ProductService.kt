package com.loopers.domain.product

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.data.domain.Slice
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class ProductService(
    private val productRepository: ProductRepository,
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
            statistic = statistic,
            brand = brand,
        )
    }

    @Transactional(readOnly = true)
    fun findProducts(command: ProductCommand.FindProducts): Slice<ProductView> {
        val pageQuery = command.to()
        val slicedProduct = productRepository.findAllBy(pageQuery)

        val slicedProductIds = slicedProduct.content.map { it.id }
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
        return productRepository.findAllByIds(ids)
    }

    @Transactional
    fun decreaseStocks(command: ProductCommand.DecreaseStocks) {
        val decreaseStockMap = command.units.associateBy { it.productId }

        val decreaseProductIds = decreaseStockMap.keys.toList()
        val lockedProducts = productRepository.findAllByIdsWithLock(decreaseProductIds)

        lockedProducts.forEach { product ->
            val decreasedStock = decreaseStockMap[product.id]
                ?: throw CoreException(
                    errorType = ErrorType.BAD_REQUEST,
                    customMessage = "[productId = ${product.id}] 상품의 재고를 감소할 수 없습니다.",
                )

            product.decreaseStock(decreasedStock.amount)
        }

        productRepository.saveAll(lockedProducts)
    }

    @Transactional
    fun increaseProductLikeCount(productId: Long) {
        productStatisticRepository.increaseLikeCountBy(productId)
    }

    @Transactional
    fun decreaseProductLikeCount(productId: Long) {
        productStatisticRepository.decreaseLikeCountBy(productId)
    }
}
