package org.example.solver;

import org.example.exception.InfeasibleProblemException;
import org.example.exception.UnboundedProblemException;
import org.example.model.Solution;

public interface ISolver {
    Solution solve() throws InfeasibleProblemException, UnboundedProblemException;
}