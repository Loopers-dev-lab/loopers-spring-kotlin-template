package com.loopers.application.brand

import com.loopers.domain.brand.BrandService
import com.loopers.domain.brand.dto.command.BrandCommand.RegisterBrand
import com.loopers.domain.brand.dto.result.BrandResult.BrandDetail
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Transactional(readOnly = true)
@Component
class BrandFacade(
    private val brandService: BrandService,
) {
    @Transactional
    fun registerBrand(command: RegisterBrand): BrandDetail {
        return BrandDetail.from(brandService.register(command))
    }
}
