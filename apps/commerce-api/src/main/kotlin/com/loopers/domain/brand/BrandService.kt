package com.loopers.domain.brand

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class BrandService(
    private val brandRepository: BrandRepository,
) {

    @Transactional(readOnly = true)
    fun getBrand(id: Long): Brand {
        return brandRepository.findById(id)
            ?: throw CoreException(ErrorType.NOT_FOUND, "[id = $id] 브랜드를 찾을 수 없습니다.")
    }
}
