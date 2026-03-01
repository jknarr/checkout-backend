package com.paze.checkout.exception;

public class OtpExpiredException extends RuntimeException {
    public OtpExpiredException(String msg) { super(msg); }
}
