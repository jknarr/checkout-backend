package com.paze.checkout.exception;

public class SessionAlreadySubmittedException extends RuntimeException {
    public SessionAlreadySubmittedException(String msg) { super(msg); }
}
