package com.loopers.domain.product

import com.loopers.domain.product.entity.ProductOption
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component

@Component
class ProductOptionService(
    private val productOptionRepository: ProductOptionRepository,
) {
    fun get(id: Long): ProductOption {
        return productOptionRepository.find(id)
            ?: throw CoreException(errorType = ErrorType.NOT_FOUND, customMessage = "[id = $id] 예시를 찾을 수 없습니다.")
    }

    fun findAll(ids: List<Long>): List<ProductOption> {
        return productOptionRepository.findAll(ids)
    }

    fun findAll(productId: Long): List<ProductOption> {
        return productOptionRepository.findAll(productId)
    }
}
