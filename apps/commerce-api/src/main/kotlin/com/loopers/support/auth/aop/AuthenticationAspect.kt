package com.loopers.support.auth.aop

import com.loopers.domain.user.UserRepository
import com.loopers.support.auth.context.AuthContext
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.springframework.stereotype.Component
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

@Aspect
@Component
class AuthenticationAspect(
    private val userRepository: UserRepository,
    private val authContext: AuthContext,
) {

    @Around("@annotation(com.loopers.support.auth.annotation.Authenticated)")
    fun validateUser(joinPoint: ProceedingJoinPoint): Any {
        val request = RequestContextHolder.getRequestAttributes()
            ?.let { it as? ServletRequestAttributes }
            ?.request
            ?: throw CoreException(ErrorType.NOT_FOUND, "요청 정보가 존재하지 않습니다.")

        val userName = request.getHeader("X-USER-ID")
            ?: throw CoreException(ErrorType.BAD_REQUEST, "인증 토큰이 누락되었습니다.")

        // TODO: 토큰 정보를 신뢰할 수 없어서 User에 의존하고 있지만 추후 리펙토링 필요
        val user = userRepository.findByUserName(userName)
            ?: throw CoreException(ErrorType.NOT_FOUND, "[userName = $userName] 존재하지 않는 유저입니다.")

        authContext.setUser(user.id, user.userName)

        return joinPoint.proceed()
    }
}
