package com.loopers.domain.product

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Component
class ProductService(
    private val productRepository: ProductRepository,
) {

    @Transactional(readOnly = true)
    fun getProduct(id: Long): Product {
        return productRepository.findById(id)
            ?: throw CoreException(ErrorType.NOT_FOUND, "[id = $id] 상품을 찾을 수 없습니다.")
    }

    @Transactional
    fun createProduct(name: String, price: BigDecimal, brandId: Long): Product {
        val product = Product.of(
            name = name,
            price = price,
            brandId = brandId,
        )
        return productRepository.save(product)
    }

    @Transactional
    fun updateProduct(id: Long, name: String?, price: BigDecimal?, brandId: Long?): Product {
        val product = getProduct(id)
        product.update(name, price, brandId)
        return productRepository.save(product)
    }

    @Transactional
    fun deleteProduct(id: Long) {
        val product = getProduct(id)
        product.delete()
    }
}
