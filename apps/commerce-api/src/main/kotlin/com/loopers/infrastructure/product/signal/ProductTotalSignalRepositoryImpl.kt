package com.loopers.infrastructure.product.signal

import com.loopers.domain.product.signal.ProductTotalSignalModel
import com.loopers.domain.product.signal.ProductTotalSignalRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component

@Component
class ProductTotalSignalRepositoryImpl(private val productTotalSignalJpaRepository: ProductTotalSignalJpaRepository) :
    ProductTotalSignalRepository {

    override fun findByProductId(productId: Long): ProductTotalSignalModel? =
        productTotalSignalJpaRepository.findByRefProductId(productId)

    override fun save(productTotalSignalModel: ProductTotalSignalModel): ProductTotalSignalModel =
        productTotalSignalJpaRepository.save(productTotalSignalModel)

    override fun getByProductId(productId: Long): ProductTotalSignalModel =
        productTotalSignalJpaRepository.findByRefProductId(productId)
            ?: throw CoreException(ErrorType.BAD_REQUEST, "상품에 대한 통계가 존재하지 않습니다.")

    override fun getByProductIdWithPessimisticLock(productId: Long): ProductTotalSignalModel =
        productTotalSignalJpaRepository.findByRefProductIdWithPessimisticLock(productId)
            ?: throw CoreException(ErrorType.BAD_REQUEST, "상품에 대한 통계가 존재하지 않습니다.")
}
