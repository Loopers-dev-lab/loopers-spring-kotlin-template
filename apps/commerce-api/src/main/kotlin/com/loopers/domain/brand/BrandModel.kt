package com.loopers.domain.brand

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "brands")
class BrandModel(
    @Column
    val name: String,
) : BaseEntity() {

    init {
        require(!name.isBlank()) { "브랜드 이름을 필수 입니다." }
    }
}
