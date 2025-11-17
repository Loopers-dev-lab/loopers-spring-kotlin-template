package com.loopers.infrastructure.product

import com.loopers.application.product.ProductInfo
import com.loopers.domain.product.ProductModel
import com.loopers.domain.product.ProductRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

@Component
class ProductRepositoryImpl(private val productJpaRepository: ProductJpaRepository) : ProductRepository {

    override fun findById(productId: Long): ProductModel? = productJpaRepository.findByIdOrNull(productId)

    override fun save(product: ProductModel): ProductModel = productJpaRepository.save(product)

    override fun findAllProductInfos(pageable: Pageable): Page<ProductInfo> = productJpaRepository.findAllProductInfos(pageable)

    override fun getProductBy(productId: Long): ProductModel =
        productJpaRepository.findByIdOrNull(productId) ?: throw CoreException(ErrorType.BAD_REQUEST, "존재하지 않는 상품입니다.")
}
