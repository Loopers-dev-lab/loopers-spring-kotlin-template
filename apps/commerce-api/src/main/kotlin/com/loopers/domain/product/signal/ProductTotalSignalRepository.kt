package com.loopers.domain.product.signal

interface ProductTotalSignalRepository {
    fun findByProductId(productId: Long): ProductTotalSignalModel?

    fun save(productTotalSignalModel: ProductTotalSignalModel): ProductTotalSignalModel

    fun getByProductId(productId: Long): ProductTotalSignalModel

    fun getByProductIdWithPessimisticLock(productId: Long): ProductTotalSignalModel
}
