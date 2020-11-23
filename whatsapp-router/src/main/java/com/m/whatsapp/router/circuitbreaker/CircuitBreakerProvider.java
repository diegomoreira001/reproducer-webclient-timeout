package com.m.whatsapp.router.circuitbreaker;

import io.vertx.circuitbreaker.CircuitBreaker;
import io.vertx.circuitbreaker.CircuitBreakerOptions;
import io.vertx.core.Vertx;

import java.util.HashMap;
import java.util.Map;

/**
 * A convenience circuit breaker registry to manage different routes.
 *
 * Non-blocking. DO NOT change to ConcurrentMap. Avoid locks.
 * Not thread-safe.
 * Meant to be used in non-blocking operation on the event-loop.
 *
 */
public class CircuitBreakerProvider {


    public static final int MAX_FAILURES = 3;// default
    public static final long TIMEOUT = 10000L; //5 sec default
    public static final long RESET_TIMEOUT = 60000L; //1 min default

    private final Map<String, CircuitBreaker> cbRegistry = new HashMap<>();
    private final Vertx vertx;

    public CircuitBreakerProvider(final Vertx vertx) {
        this.vertx = vertx;
    }

    public CircuitBreaker getOrCreate(String key) {

        CircuitBreaker cb = cbRegistry.get(key);
        if (cb == null) {
            cb = CircuitBreaker.create(key, vertx,
                    new CircuitBreakerOptions()
                            .setMaxFailures(MAX_FAILURES) // number of failure before opening the circuit
                            .setTimeout(TIMEOUT) // consider a failure if the operation does not succeed in time
                            .setFallbackOnFailure(true) // we'll fallback to a fail-fast error
                            .setResetTimeout(RESET_TIMEOUT) // time spent in open state before attempting to re-try
            );
            cbRegistry.put(key, cb);
        }

        return cb;
    }
}
