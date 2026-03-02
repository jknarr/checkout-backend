package com.paze.checkout.domain;

public enum AuthStep {
    OTP_VERIFY,
    CARD_SELECT,
    CVV,
    REVIEW,
    PASSKEY_REGISTER,
    PASSKEY_AUTH,
    COMPLETED
}
