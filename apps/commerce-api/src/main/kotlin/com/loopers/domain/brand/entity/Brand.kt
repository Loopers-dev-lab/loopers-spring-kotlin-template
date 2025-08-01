package com.loopers.domain.brand.entity

import com.loopers.domain.BaseEntity
import com.loopers.domain.brand.vo.BrandDescription
import com.loopers.domain.brand.vo.BrandName
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "brand")
class Brand protected constructor(
    name: BrandName,
    description: BrandDescription,
) : BaseEntity() {

    @Column(name = "name", nullable = false)
    var name: BrandName = name
        protected set

    @Column(name = "description", nullable = false)
    var description: BrandDescription = description
        protected set

    companion object {
        fun create(name: String, description: String): Brand {
            return Brand(BrandName(name), BrandDescription(description))
        }
    }
}
