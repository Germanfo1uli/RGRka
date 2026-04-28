package org.example.exception;

public class UnboundedProblemException extends Exception {
    public UnboundedProblemException(String message) {
        super(message);
    }

    public UnboundedProblemException(String message, Throwable cause) {
        super(message, cause);
    }
}