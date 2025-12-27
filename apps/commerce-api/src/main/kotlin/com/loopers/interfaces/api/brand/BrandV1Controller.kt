package com.loopers.interfaces.api.brand

import com.loopers.application.brand.BrandFacade
import com.loopers.interfaces.api.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/brands")
class BrandV1Controller(private val brandFacade: BrandFacade) : BrandV1ApiSpec {
    @GetMapping("/{brandId}")
    override fun getBrand(
        @PathVariable(value = "brandId") brandId: Long,
    ): ApiResponse<BrandV1Dto.BrandResponse> = brandFacade.getBrand(brandId)
            .let { BrandV1Dto.BrandResponse.from(it) }
            .let { ApiResponse.success(it) }
}
