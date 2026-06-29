package com.jzqs.app.common.aop.aspect;

import com.jzqs.app.common.aop.annotation.AuditAction;
import com.jzqs.app.common.security.AdminRequestContext;
import com.jzqs.app.common.security.AdminRequestContextSupport;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class AuditActionAspect {
    private static final Logger log = LoggerFactory.getLogger(AuditActionAspect.class);

    @Around("@annotation(auditAction)")
    public Object around(ProceedingJoinPoint joinPoint, AuditAction auditAction) throws Throwable {
        long startedAt = System.currentTimeMillis();
        AdminRequestContext admin = AdminRequestContextSupport.requireAdmin();
        try {
            Object result = joinPoint.proceed();
            log.info(
                "AUDIT module={} action={} operatorId={} operatorName={} path={} status=SUCCESS durationMs={}",
                auditAction.module(),
                auditAction.action(),
                admin.userId(),
                admin.operatorName(),
                AdminRequestContextSupport.currentRequestPath(),
                System.currentTimeMillis() - startedAt
            );
            return result;
        } catch (Throwable throwable) {
            log.warn(
                "AUDIT module={} action={} operatorId={} operatorName={} path={} status=FAILED durationMs={} error={}",
                auditAction.module(),
                auditAction.action(),
                admin.userId(),
                admin.operatorName(),
                AdminRequestContextSupport.currentRequestPath(),
                System.currentTimeMillis() - startedAt,
                throwable.getClass().getSimpleName()
            );
            throw throwable;
        }
    }
}
