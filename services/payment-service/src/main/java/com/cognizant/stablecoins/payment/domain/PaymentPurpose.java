package com.cognizant.stablecoins.payment.domain;

/**
 * Supported payment purposes for the POC.
 * SUPPLIER_INVOICE is the primary demonstration purpose (docs/01-POC-VISION.md).
 */
public enum PaymentPurpose {
    SUPPLIER_INVOICE,
    TRADE_SETTLEMENT,
    TREASURY_TRANSFER,
    INTERCOMPANY_TRANSFER
}
