package com.loopers.domain.example

import com.loopers.domain.BaseEntity
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "example")
class ExampleModel(
    name: String,
    description: String,
) : BaseEntity() {
    var name: String = name
        protected set

    var description: String = description
        protected set

    init {
        if (name.isBlank()) throw CoreException(ErrorType.BAD_REQUEST, "이름은 비어있을 수 없습니다.")
        if (description.isBlank()) throw CoreException(ErrorType.BAD_REQUEST, "설명은 비어있을 수 없습니다.")
    }

    fun update(newDescription: String) {
        if (newDescription.isBlank()) throw CoreException(ErrorType.BAD_REQUEST, "설명은 비어있을 수 없습니다.")
        this.description = newDescription
    }
}
