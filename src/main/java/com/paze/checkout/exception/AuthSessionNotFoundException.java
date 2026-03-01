package com.paze.checkout.exception;

public class AuthSessionNotFoundException extends RuntimeException {
    public AuthSessionNotFoundException(String msg) { super(msg); }
}
