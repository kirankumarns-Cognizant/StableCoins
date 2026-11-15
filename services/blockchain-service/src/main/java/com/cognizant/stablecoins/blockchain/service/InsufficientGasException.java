package com.cognizant.stablecoins.blockchain.service;

public class InsufficientGasException extends RuntimeException {

    public InsufficientGasException(String message) {
        super(message);
    }
}
