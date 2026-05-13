package org.example.solver;

import org.example.exception.InfeasibleProblemException;
import org.example.io.OutputWriter;
import org.example.model.Fraction;
import org.example.model.LinearProblem;
import org.example.model.Solution;

import java.util.Arrays;

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
        OutputWriter.printAugmentedMatrix("Начальная расширенная матрица:", matrix, n, null);

        normalizeNegativeRightSides();
        validateNoSignContradictions();

        int[] basis = new int[m];
        Arrays.fill(basis, -1);

        assignReadyBasisColumns(basis);
        if (isCompleteBasis(basis)) {
            OutputWriter.printOperation("\nКанонический вид получен. Строим опорное решение по базисным переменным.");
            return buildSolution(basis);
        }

        int iteration = countAssignedRows(basis);
        int pivotRow = firstUnassignedRow(basis);

        for (int col = 0; col < n && pivotRow != -1; col++) {
            if (isBasisColumn(col, basis)) {
                continue;
            }

            int selectedRow = findPivotRow(pivotRow, col, basis);
            if (selectedRow == -1) {
                OutputWriter.printOperation("Столбец x" + (col + 1) + " пропущен: нет положительного элемента");
                continue;
            }

            iteration++;
            OutputWriter.printSection("Шаг Жордана-Гаусса #" + iteration);
            OutputWriter.printOperation("Ведущий столбец: x" + (col + 1));
            OutputWriter.printOperation("Выбран ведущий элемент в строке R" + (selectedRow + 1));

            if (selectedRow != pivotRow) {
                OutputWriter.printOperation("Меняем местами строки R" + (pivotRow + 1) + " и R" + (selectedRow + 1));
                swapRows(pivotRow, selectedRow);
                swapBasisLabels(basis, pivotRow, selectedRow);
                OutputWriter.printAugmentedMatrix("Матрица после перестановки строк:", matrix, n, basis);
            }

            OutputWriter.printOperation(
                    "Разрешающий элемент: a[" + (pivotRow + 1) + "][" + (col + 1) + "] = " + matrix[pivotRow][col] +
                            ", b/a = " + matrix[pivotRow][n] + " / " + matrix[pivotRow][col] + " = " + matrix[pivotRow][n].divide(matrix[pivotRow][col])
            );

            normalizePivotRow(pivotRow, col);
            eliminateColumn(pivotRow, col);
            validateNoSignContradictions();

            basis[pivotRow] = col;
            OutputWriter.printAugmentedMatrix("Матрица после исключения переменной из остальных строк:", matrix, n, basis);
            pivotRow = firstUnassignedRow(basis);
        }

        checkConsistency();

        if (!isCompleteBasis(basis)) {
            throw new InfeasibleProblemException("Опорного решения нет.");
        }

        OutputWriter.printOperation("\nКанонический вид получен. Строим опорное решение по базисным переменным.");
        return buildSolution(basis);
    }

    private void normalizeNegativeRightSides() {
        for (int row = 0; row < m; row++) {
            if (!matrix[row][n].isNegative()) {
                continue;
            }

            OutputWriter.printOperation("Нормализация b: умножение строки R" + (row + 1) + " на -1");
            multiplyRowByMinusOne(row);
            OutputWriter.printAugmentedMatrix("После нормализации b" + (row + 1) + ":", matrix, n, null);
        }
    }

    private void assignReadyBasisColumns(int[] basis) {
        for (int col = 0; col < n; col++) {
            int row = findUnitColumnRow(col);
            if (row == -1 || basis[row] != -1) {
                continue;
            }

            basis[row] = col;
            OutputWriter.printOperation("Найден готовый базисный столбец: x" + (col + 1));
            OutputWriter.printAugmentedMatrix("Базис: x" + (col + 1) + " (единичный столбец)", matrix, n, basis);
        }
    }

    private int findUnitColumnRow(int col) {
        int unitRow = -1;

        for (int row = 0; row < m; row++) {
            Fraction value = matrix[row][col];
            if (value.equals(Fraction.ONE)) {
                if (unitRow != -1) {
                    return -1;
                }
                unitRow = row;
            } else if (!value.isZero()) {
                return -1;
            }
        }

        return unitRow;
    }

    private int findPivotRow(int startRow, int col, int[] basis) {
        int selectedRow = -1;
        Fraction bestRatio = null;

        for (int row = startRow; row < m; row++) {
            if (basis[row] != -1) {
                continue;
            }

            Fraction coefficient = matrix[row][col];
            if (!coefficient.isPositive()) {
                continue;
            }

            Fraction ratio = matrix[row][n].divide(coefficient);
            if (ratio.isNegative()) {
                continue;
            }

            if (bestRatio == null || ratio.compareTo(bestRatio) < 0) {
                bestRatio = ratio;
                selectedRow = row;
            }
        }

        return selectedRow;
    }

    private boolean isCompleteBasis(int[] basis) {
        for (int basisVar : basis) {
            if (basisVar < 0) {
                return false;
            }
        }
        return true;
    }

    private int countAssignedRows(int[] basis) {
        int count = 0;
        for (int basisVar : basis) {
            if (basisVar >= 0) {
                count++;
            }
        }
        return count;
    }

    private int firstUnassignedRow(int[] basis) {
        for (int row = 0; row < basis.length; row++) {
            if (basis[row] < 0) {
                return row;
            }
        }
        return -1;
    }

    private boolean isBasisColumn(int col, int[] basis) {
        for (int basisVar : basis) {
            if (basisVar == col) {
                return true;
            }
        }
        return false;
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

    private void multiplyRowByMinusOne(int row) {
        for (int col = 0; col <= n; col++) {
            matrix[row][col] = matrix[row][col].negate();
        }
    }

    private void swapRows(int firstRow, int secondRow) {
        Fraction[] temp = matrix[firstRow];
        matrix[firstRow] = matrix[secondRow];
        matrix[secondRow] = temp;
    }

    private void swapBasisLabels(int[] basis, int firstRow, int secondRow) {
        int temp = basis[firstRow];
        basis[firstRow] = basis[secondRow];
        basis[secondRow] = temp;
    }

    private void checkConsistency() throws InfeasibleProblemException {
        for (int i = 0; i < m; i++) {
            boolean zeroRow = true;
            for (int j = 0; j < n; j++) {
                if (!matrix[i][j].isZero()) {
                    zeroRow = false;
                    break;
                }
            }

            if (zeroRow && !matrix[i][n].isZero()) {
                throw new InfeasibleProblemException("Система несовместна: строка " + (i + 1) + " содержит противоречие");
            }
        }
    }

    private void validateNoSignContradictions() throws InfeasibleProblemException {
        for (int row = 0; row < m; row++) {
            boolean allNonNegative = true;
            boolean allNonPositive = true;

            for (int col = 0; col < n; col++) {
                if (matrix[row][col].isNegative()) {
                    allNonNegative = false;
                }
                if (matrix[row][col].isPositive()) {
                    allNonPositive = false;
                }
            }

            if (allNonNegative && matrix[row][n].isNegative()) {
                throw new InfeasibleProblemException(
                        "На первом этапе найдено противоречие: в строке " + (row + 1) +
                                " левая часть не может быть отрицательной, а правая часть равна " + matrix[row][n]
                );
            }

            if (allNonPositive && matrix[row][n].isPositive()) {
                throw new InfeasibleProblemException(
                        "На первом этапе найдено противоречие: в строке " + (row + 1) +
                                " левая часть не может быть положительной, а правая часть равна " + matrix[row][n]
                );
            }
        }
    }

    private Solution buildSolution(int[] basis) throws InfeasibleProblemException {
        Fraction[] values = new Fraction[n];
        Arrays.fill(values, Fraction.ZERO);

        for (int row = 0; row < m; row++) {
            int basisVar = basis[row];
            if (basisVar < 0) {
                throw new InfeasibleProblemException("Не удалось определить базисную переменную для строки " + (row + 1));
            }

            Fraction value = matrix[row][n];
            if (value.isNegative()) {
                throw new InfeasibleProblemException(
                        "Найденное базисное решение не является допустимым: x" + (basisVar + 1) + " = " + value
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
