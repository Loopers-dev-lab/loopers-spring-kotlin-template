package com.loopers.support.auth.context

import com.loopers.domain.user.User.UserName
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.web.context.annotation.RequestScope

// TODO: 현재 유저 인증 정보를 신뢰할 수 없어 User를 의존하는 상황 발생. 추후 리펙토링 필요
@Component
@RequestScope
class AuthContext {

    private var id: Long? = null
    private var userName: UserName? = null

    fun setUser(id: Long, userName: UserName) {
        this.id = id
        this.userName = userName
    }

    fun getId(): Long {
        return id ?: throw CoreException(ErrorType.BAD_REQUEST, "인증 정보가 설정되지 않았습니다.")
    }

    fun getUserName(): UserName {
        return userName ?: throw CoreException(ErrorType.BAD_REQUEST, "인증 정보가 설정되지 않았습니다.")
    }
}
