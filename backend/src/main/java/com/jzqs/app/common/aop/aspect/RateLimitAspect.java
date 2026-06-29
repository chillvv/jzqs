package com.jzqs.app.common.aop.aspect;

import com.jzqs.app.common.aop.annotation.RateLimit;
import com.jzqs.app.common.aop.store.InMemoryRateLimitStore;
import com.jzqs.app.common.error.BusinessException;
import com.jzqs.app.common.error.ErrorCode;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class RateLimitAspect {
    private final InMemoryRateLimitStore rateLimitStore;

    public RateLimitAspect(InMemoryRateLimitStore rateLimitStore) {
        this.rateLimitStore = rateLimitStore;
    }

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        String key = AopRequestKeySupport.rateLimitKey(rateLimit.key());
        boolean allowed = rateLimitStore.allow(key, rateLimit.maxRequests(), rateLimit.windowSeconds());
        if (!allowed) {
            throw new BusinessException(ErrorCode.RATE_LIMITED, "请求过于频繁，请稍后重试");
        }
        return joinPoint.proceed();
    }
}
