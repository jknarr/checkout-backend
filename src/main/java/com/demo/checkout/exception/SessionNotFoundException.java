package com.demo.checkout.exception;

public class SessionNotFoundException extends RuntimeException {
    public SessionNotFoundException(String msg) { super(msg); }
}
