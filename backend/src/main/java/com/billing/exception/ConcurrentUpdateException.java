package com.billing.exception;

/** Thrown when a subscription mutation loses an optimistic-lock race
 *  (e.g. two concurrent upgrade requests hit the same subscription row). */
public class ConcurrentUpdateException extends RuntimeException {
    public ConcurrentUpdateException(String message) {
        super(message);
    }
}
