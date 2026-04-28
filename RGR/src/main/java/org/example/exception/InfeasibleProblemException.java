package org.example.exception;

public class InfeasibleProblemException extends Exception {
    public InfeasibleProblemException(String message) {
        super(message);
    }

    public InfeasibleProblemException(String message, Throwable cause) {
        super(message, cause);
    }
}