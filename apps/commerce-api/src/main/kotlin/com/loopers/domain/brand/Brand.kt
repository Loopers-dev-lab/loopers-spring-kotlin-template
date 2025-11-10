package com.loopers.domain.brand

import com.loopers.domain.BaseEntity
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "loopers_brand")
class Brand(
    @Column(unique = true, nullable = false, length = 100)
    var name: String,

    ) : BaseEntity() {

    init {
        validateName(name)
    }

    companion object {
        fun of(name: String): Brand {
            return Brand(
                name = name,
            )
        }
    }

    private fun validateName(name: String) {
        if (name.isBlank()) {
            throw CoreException(ErrorType.BAD_REQUEST, "브랜드 이름은 비어있을 수 없습니다.")
        }
    }
}
