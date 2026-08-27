package com.jzqs.app.common.aop.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jzqs.app.common.aop.annotation.Idempotent;
import com.jzqs.app.common.aop.store.DbIdempotencyStore;
import com.jzqs.app.common.error.BusinessException;
import com.jzqs.app.common.error.ErrorCode;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class IdempotentAspect {
    private static final Logger log = LoggerFactory.getLogger(IdempotentAspect.class);

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
            // 幂等命中：记录重复提交来源，便于排查"重复下单/重复扣餐"类问题
            log.warn("幂等拦截重复提交: key={} method={}", key, joinPoint.getSignature().toShortString());
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
