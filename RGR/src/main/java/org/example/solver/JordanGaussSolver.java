package org.example.solver;

import org.example.exception.InfeasibleProblemException;
import org.example.model.Fraction;
import org.example.model.LinearProblem;
import org.example.model.Solution;

public class JordanGaussSolver implements ISolver {
    private final LinearProblem problem;
    private final Fraction[][] matrix;
    private final int m;
    private final int n;

    public JordanGaussSolver(LinearProblem problem) {
        this.problem = problem;
        this.m = problem.getM();
        this.n = problem.getN();
        this.matrix = initializeMatrix();
    }

    private Fraction[][] initializeMatrix() {
        Fraction[][] result = new Fraction[m][n + 1];
        Fraction[][] constraints = problem.getConstraints();
        Fraction[] rightSide = problem.getRightSide();

        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                result[i][j] = constraints[i][j];
            }
            result[i][n] = rightSide[i];
        }

        return result;
    }

    @Override
    public Solution solve() throws InfeasibleProblemException {
        System.out.println("Поиск опорного решения методом Жордана-Гаусса...");

        int[] basis = new int[m];
        for (int i = 0; i < m; i++) {
            basis[i] = -1;
        }

        int pivotRow = 0;

        for (int col = 0; col < n && pivotRow < m; col++) {
            int selectedRow = findPivotRow(pivotRow, col);
            if (selectedRow == -1) {
                continue;
            }

            if (selectedRow != pivotRow) {
                swapRows(pivotRow, selectedRow);
            }

            normalizePivotRow(pivotRow, col);
            eliminateColumn(pivotRow, col);
            basis[pivotRow] = col;
            pivotRow++;
        }

        checkConsistency(pivotRow);

        if (pivotRow < m) {
            throw new InfeasibleProblemException(
                    "Не удалось построить полное опорное решение: ранг системы меньше числа ограничений"
            );
        }

        return buildSolution(basis);
    }

    private int findPivotRow(int startRow, int col) {
        int selectedRow = -1;
        Fraction bestAbsValue = Fraction.ZERO;

        for (int i = startRow; i < m; i++) {
            Fraction value = matrix[i][col].abs();
            if (!value.isZero() && value.compareTo(bestAbsValue) > 0) {
                bestAbsValue = value;
                selectedRow = i;
            }
        }

        return selectedRow;
    }

    private void normalizePivotRow(int row, int col) {
        Fraction pivot = matrix[row][col];
        for (int j = 0; j <= n; j++) {
            matrix[row][j] = matrix[row][j].divide(pivot);
        }
    }

    private void eliminateColumn(int pivotRow, int pivotCol) {
        for (int i = 0; i < m; i++) {
            if (i == pivotRow) {
                continue;
            }

            Fraction factor = matrix[i][pivotCol];
            if (factor.isZero()) {
                continue;
            }

            for (int j = 0; j <= n; j++) {
                matrix[i][j] = matrix[i][j].subtract(factor.multiply(matrix[pivotRow][j]));
            }
        }
    }

    private void swapRows(int firstRow, int secondRow) {
        Fraction[] temp = matrix[firstRow];
        matrix[firstRow] = matrix[secondRow];
        matrix[secondRow] = temp;
    }

    private void checkConsistency(int rank) throws InfeasibleProblemException {
        for (int i = rank; i < m; i++) {
            boolean zeroRow = true;
            for (int j = 0; j < n; j++) {
                if (!matrix[i][j].isZero()) {
                    zeroRow = false;
                    break;
                }
            }

            if (zeroRow && !matrix[i][n].isZero()) {
                throw new InfeasibleProblemException(
                        "Система несовместна: строка " + (i + 1) + " содержит противоречие"
                );
            }
        }
    }

    private Solution buildSolution(int[] basis) throws InfeasibleProblemException {
        Fraction[] values = new Fraction[n];
        for (int i = 0; i < n; i++) {
            values[i] = Fraction.ZERO;
        }

        for (int row = 0; row < m; row++) {
            int basisVar = basis[row];
            if (basisVar < 0) {
                throw new InfeasibleProblemException("Не удалось определить базисную переменную для строки " + (row + 1));
            }

            Fraction value = matrix[row][n];
            if (value.isNegative()) {
                throw new InfeasibleProblemException(
                        "Найденное базисное решение не является допустимым: x" +
                                (basisVar + 1) + " = " + value
                );
            }

            values[basisVar] = value;
        }

        Fraction objectiveValue = Fraction.ZERO;
        Fraction[] objective = problem.getObjective();
        for (int i = 0; i < n; i++) {
            objectiveValue = objectiveValue.add(objective[i].multiply(values[i]));
        }

        return new Solution.Builder(values, objectiveValue, basis.clone()).build();
    }
}
