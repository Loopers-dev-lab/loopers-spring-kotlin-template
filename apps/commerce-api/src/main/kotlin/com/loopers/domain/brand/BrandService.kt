package com.loopers.domain.brand

import com.loopers.domain.brand.dto.command.BrandCommand
import com.loopers.domain.brand.entity.Brand
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component

@Component
class BrandService(
    private val brandRepository: BrandRepository,
) {
    fun get(id: Long): Brand {
        return brandRepository.find(id)
            ?: throw CoreException(errorType = ErrorType.NOT_FOUND, customMessage = "[id = $id] 예시를 찾을 수 없습니다.")
    }

    fun findAll(ids: List<Long>): List<Brand> {
        return brandRepository.findAll(ids)
    }

    fun register(command: BrandCommand.RegisterBrand): Brand {
        return brandRepository.save(command.toEntity())
    }
}
