package com.adp.gateway.dataaccess.application;

public class DataAccessDeniedException extends RuntimeException {

    public DataAccessDeniedException(String message) {
        super(message);
    }
}
