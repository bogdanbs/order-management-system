package com.orders.infrastructure.concurrency;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

// Must wrap @Transactional (default order LOWEST_PRECEDENCE): PostgreSQL aborts the transaction on a
// conflict, so each attempt needs a fresh one.
@Aspect
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 1)
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
