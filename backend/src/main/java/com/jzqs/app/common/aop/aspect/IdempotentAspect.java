package com.jzqs.app.common.aop.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jzqs.app.common.aop.annotation.Idempotent;
import com.jzqs.app.common.aop.store.DbIdempotencyStore;
import com.jzqs.app.common.error.BusinessException;
import com.jzqs.app.common.error.ErrorCode;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class IdempotentAspect {
    private final DbIdempotencyStore idempotencyStore;
    private final ObjectMapper objectMapper;

    public IdempotentAspect(DbIdempotencyStore idempotencyStore, ObjectMapper objectMapper) {
        this.idempotencyStore = idempotencyStore;
        this.objectMapper = objectMapper;
    }

    @Around("@annotation(idempotent)")
    public Object around(ProceedingJoinPoint joinPoint, Idempotent idempotent) throws Throwable {
        String key = AopRequestKeySupport.idempotencyKey(idempotent.key(), joinPoint, objectMapper, idempotent.includeBody());
        if (!idempotencyStore.acquire(key, idempotent.ttlSeconds())) {
            throw new BusinessException(ErrorCode.REPEAT_SUBMISSION, "请勿重复提交相同操作");
        }
        try {
            Object result = joinPoint.proceed();
            idempotencyStore.markSucceeded(key, idempotent.ttlSeconds());
            return result;
        } catch (Throwable throwable) {
            idempotencyStore.release(key);
            throw throwable;
        }
    }
}
