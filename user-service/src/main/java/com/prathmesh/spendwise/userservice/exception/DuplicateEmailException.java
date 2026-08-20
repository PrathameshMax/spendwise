package com.prathmesh.spendwise.userservice.exception;

public class DuplicateEmailException extends RuntimeException{

    public DuplicateEmailException(String message){
        super(message);
    }
}
