package com.loopers.infrastructure.brand

import com.loopers.domain.brand.BrandModel
import org.springframework.data.jpa.repository.JpaRepository

interface BrandJpaRepository : JpaRepository<BrandModel, Long>
