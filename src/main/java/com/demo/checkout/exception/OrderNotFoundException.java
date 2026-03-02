package com.demo.checkout.exception;

public class OrderNotFoundException extends RuntimeException {
    public OrderNotFoundException(String msg) { super(msg); }
}
