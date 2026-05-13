package org.example.solver;

import org.example.exception.InfeasibleProblemException;
import org.example.exception.InvalidInputException;
import org.example.model.Fraction;
import org.example.model.LinearProblem;
import org.example.model.Solution;

import java.util.Arrays;

public class PhaseOneSolver {
    private final LinearProblem originalProblem;

    public PhaseOneSolver(LinearProblem problem) {
        this.originalProblem = problem;
    }

    public Solution findFeasibleBasis() throws InfeasibleProblemException {
        int m = originalProblem.getM();
        int n = originalProblem.getN();
        Fraction[][] origConstraints = originalProblem.getConstraints();
        Fraction[] origRHS = originalProblem.getRightSide();

        Fraction[][] constraints = new Fraction[m][];
        Fraction[] rhs = new Fraction[m];
        for (int i = 0; i < m; i++) {
            if (origRHS[i].isNegative()) {
                constraints[i] = new Fraction[n];
                for (int j = 0; j < n; j++) {
                    constraints[i][j] = origConstraints[i][j].negate();
                }
                rhs[i] = origRHS[i].negate();
            } else {
                constraints[i] = Arrays.copyOf(origConstraints[i], n);
                rhs[i] = origRHS[i];
            }
        }

        int artificialCount = m;
        int totalVars = n + artificialCount;

        Fraction[][] auxConstraints = new Fraction[m][totalVars];
        Fraction[] auxRHS = new Fraction[m];
        Fraction[] auxObjective = new Fraction[totalVars];

        for (int i = 0; i < m; i++) {
            System.arraycopy(constraints[i], 0, auxConstraints[i], 0, n);
            for (int j = 0; j < m; j++) {
                auxConstraints[i][n + j] = (i == j) ? Fraction.ONE : Fraction.ZERO;
            }
            auxRHS[i] = rhs[i];
        }

        Arrays.fill(auxObjective, Fraction.ZERO);
        for (int j = n; j < totalVars; j++) {
            auxObjective[j] = Fraction.ONE;
        }

        LinearProblem auxProblem;
        try {
            auxProblem = new LinearProblem(auxConstraints, auxRHS, auxObjective, LinearProblem.ObjectiveType.MIN);
        } catch (InvalidInputException e) {
            throw new InfeasibleProblemException("Не удалось построить вспомогательную задачу", e);
        }

        int[] initialBasis = new int[m];
        for (int i = 0; i < m; i++) {
            initialBasis[i] = n + i;
        }
        Fraction[] initialValues = new Fraction[totalVars];
        Arrays.fill(initialValues, Fraction.ZERO);
        for (int i = 0; i < m; i++) {
            initialValues[n + i] = rhs[i];
        }

        Solution initialPhaseOneSolution = new Solution.Builder(
                initialValues,
                computeSumArtificials(initialValues, n, m),
                initialBasis
        ).build();

        SimplexSolver simplex = new SimplexSolver(auxProblem, initialPhaseOneSolution, false);
        Solution phaseOneSolution;
        try {
            phaseOneSolution = simplex.solve();
        } catch (Exception e) {
            throw new InfeasibleProblemException("Phase I завершился с ошибкой: " + e.getMessage(), e);
        }

        if (!phaseOneSolution.getObjectiveValue().isZero()) {
            throw new InfeasibleProblemException(
                    "Искусственные переменные не могут быть обнулены - задача несовместна"
            );
        }

        int[] originalBasis = new int[m];
        Fraction[] originalValues = new Fraction[n];
        Arrays.fill(originalValues, Fraction.ZERO);
        int[] auxBasis = phaseOneSolution.getBasis();
        Fraction[] auxValues = phaseOneSolution.getValues();

        for (int i = 0; i < m; i++) {
            int var = auxBasis[i];
            if (var >= n) {
                throw new InfeasibleProblemException(
                        "Не удалось вывести искусственные переменные из базиса"
                );
            }
            originalBasis[i] = var;
            originalValues[var] = auxValues[var];
        }

        return new Solution.Builder(originalValues, Fraction.ZERO, originalBasis).build();
    }

    private Fraction computeSumArtificials(Fraction[] values, int start, int count) {
        Fraction sum = Fraction.ZERO;
        for (int i = start; i < start + count; i++) {
            sum = sum.add(values[i]);
        }
        return sum;
    }
}
