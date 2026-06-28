package com.yala.model;

/** Lifecycle of a seller application: PENDING until Didit KYC resolves it. */
public enum SellerApplicationStatus {
    PENDING,
    APPROVED,
    REJECTED
}
