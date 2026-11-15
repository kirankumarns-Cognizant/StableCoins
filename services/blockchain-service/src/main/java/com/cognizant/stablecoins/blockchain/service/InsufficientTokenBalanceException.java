package com.cognizant.stablecoins.blockchain.service;

public class InsufficientTokenBalanceException extends RuntimeException {

    public InsufficientTokenBalanceException(String message) {
        super(message);
    }
}
