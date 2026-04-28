package org.example.io;

import org.example.exception.InvalidInputException;
import org.example.model.LinearProblem;
import java.io.IOException;

public interface IInputReader {
    LinearProblem readProblem(String filename) throws IOException, InvalidInputException;
}