package com.loopers.domain.shared

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.Transient
import java.util.regex.Pattern

@Embeddable
data class Email(
    @Column(name = "email", nullable = false)
    val address: String,
) {
    @Transient
    private val EMAIL_PATTERN: Pattern = Pattern.compile("^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$")
    init {
        if (!EMAIL_PATTERN.matcher(address).matches()) {
            throw CoreException(ErrorType.BAD_REQUEST,"이메일 형식이 올바르지 않습니다")
        }
    }
}
