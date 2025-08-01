package com.loopers.domain.product

import com.loopers.domain.product.dto.command.ProductCommand
import com.loopers.domain.product.dto.criteria.ProductCriteria
import com.loopers.domain.product.entity.Product
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.data.domain.Page
import org.springframework.stereotype.Component

@Component
class ProductService(
    private val productRepository: ProductRepository,
) {
    fun get(id: Long): Product {
        return productRepository.find(id)
            ?: throw CoreException(errorType = ErrorType.NOT_FOUND, customMessage = "[id = $id] 예시를 찾을 수 없습니다.")
    }

    fun findAll(ids: List<Long>): List<Product> {
        return productRepository.findAll(ids)
    }

    fun findAll(criteria: ProductCriteria.FindAll): Page<Product> {
        return productRepository.findAll(criteria)
    }

    fun register(command: ProductCommand.RegisterProduct): Product {
        return productRepository.save(command.toEntity())
    }
}
