package com.loopers.domain.brand

import com.loopers.domain.BaseEntity
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "brands")
class Brand(
    name: String,
    description: String?,
) : BaseEntity() {
    @Column(nullable = false, unique = true, length = 100)
    var name: String = name
        protected set

    @Column(columnDefinition = "TEXT")
    var description: String? = description
        protected set

    init {
        if (name.isBlank()) {
            throw CoreException(ErrorType.BAD_REQUEST, "브랜드 이름은 비어있을 수 없습니다.")
        }
    }
}
