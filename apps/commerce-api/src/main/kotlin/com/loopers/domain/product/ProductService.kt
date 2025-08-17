package com.loopers.domain.product

import com.fasterxml.jackson.core.type.TypeReference
import com.loopers.domain.product.cache.ProductCacheKey
import com.loopers.domain.product.dto.command.ProductCommand
import com.loopers.domain.product.dto.criteria.ProductCriteria
import com.loopers.domain.product.entity.Product
import com.loopers.domain.product.policy.ProductCachePolicy
import com.loopers.support.cache.GenericCacheRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class ProductService(
    private val productRepository: ProductRepository,
    private val cacheRepository: GenericCacheRepository,
) {
    fun get(id: Long): Product {
        return productRepository.find(id)
            ?: throw CoreException(errorType = ErrorType.NOT_FOUND, customMessage = "[id = $id] 예시를 찾을 수 없습니다.")
    }

    fun findAll(ids: List<Long>): List<Product> {
        return productRepository.findAll(ids)
    }

    fun findAllCached(criteria: ProductCriteria.FindAll): Page<Product> {
        val list: List<Product> = productRepository.findAll(criteria)

        val total: Long = cacheRepository.cacheAside(
            kind = "count",
            policy = ProductCachePolicy,
            key = ProductCacheKey.countKey(criteria),
            typeRef = object : TypeReference<Long>() {},
        ) {
            productRepository.count(criteria)
        }

        val pageable = PageRequest.of(criteria.page, criteria.size)
        return PageImpl(list, pageable, total)
    }

    @Transactional
    fun register(command: ProductCommand.RegisterProduct): Product {
        val product = productRepository.save(command.toEntity())
        return product
    }

    @Transactional
    fun update(command: ProductCommand.UpdateProduct) {
        val product = get(command.productId)
        product.update(command.name, command.description, command.price)
    }

    @Transactional
    fun delete(productId: Long) {
        val product = get(productId)
        product.delete()
    }
}
