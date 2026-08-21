package com.prathmesh.spendwise.transactionservice.exception;

public class TransactionNotFoundException extends RuntimeException{

    public TransactionNotFoundException(String message) {
        super(message);
    }
}
