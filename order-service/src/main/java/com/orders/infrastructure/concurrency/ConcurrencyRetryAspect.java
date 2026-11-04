package com.orders.infrastructure.concurrency;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class ConcurrencyRetryAspect {
    private static final Logger log = LoggerFactory.getLogger(ConcurrencyRetryAspect.class);

    @Around("@annotation(retryOnConcurrency)")
    public Object retry(ProceedingJoinPoint pjp, RetryOnConcurrency retryOnConcurrency) throws Throwable {
        int maxAttempts = retryOnConcurrency.maxAttempts();
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return pjp.proceed();
            } catch (OptimisticConcurrencyException e) {
                if (attempt == maxAttempts) {
                    throw e;
                }
                log.warn("Concurrency conflict on attempt {}/{}, retrying...", attempt, maxAttempts);
            }
        }
        throw new OptimisticConcurrencyException("Max retry attempts exceeded", null);
    }
}
