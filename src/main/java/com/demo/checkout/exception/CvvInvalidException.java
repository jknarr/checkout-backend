package com.demo.checkout.exception;

public class CvvInvalidException extends RuntimeException {
    public CvvInvalidException(String msg) { super(msg); }
}
