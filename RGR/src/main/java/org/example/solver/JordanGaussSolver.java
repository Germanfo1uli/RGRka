package org.example.solver;

import org.example.exception.InfeasibleProblemException;
import org.example.io.OutputWriter;
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
        OutputWriter.printAugmentedMatrix("Начальная расширенная матрица:", matrix, n, null);
        validateNoSignContradictions();

        int[] basis = new int[m];
        for (int i = 0; i < m; i++) {
            basis[i] = -1;
        }

        int pivotRow = 0;

        for (int col = 0; col < n && pivotRow < m; col++) {
            int selectedRow = findPivotRow(pivotRow, col);
            if (selectedRow == -1) {
                OutputWriter.printOperation("\nСтолбец x" + (col + 1) + " пропускается: нет подходящего ведущего элемента.");
                continue;
            }

            OutputWriter.printSection("Шаг Жордана-Гаусса #" + (pivotRow + 1));
            OutputWriter.printOperation("Ведущий столбец: x" + (col + 1));
            OutputWriter.printOperation("Выбран ведущий элемент в строке R" + (selectedRow + 1));

            if (selectedRow != pivotRow) {
                OutputWriter.printOperation("Меняем местами строки R" + (pivotRow + 1) + " и R" + (selectedRow + 1));
                swapRows(pivotRow, selectedRow);
                OutputWriter.printAugmentedMatrix("Матрица после перестановки строк:", matrix, n, basis);
            }

            OutputWriter.printOperation(
                    "Делим строку R" + (pivotRow + 1) + " на ведущий элемент " + matrix[pivotRow][col]
            );
            normalizePivotRow(pivotRow, col);
            OutputWriter.printAugmentedMatrix("После нормализации ведущей строки:", matrix, n, basis);

            OutputWriter.printOperation("Обнуляем остальные элементы в столбце x" + (col + 1));
            eliminateColumn(pivotRow, col);
            validateNoSignContradictions();

            basis[pivotRow] = col;
            OutputWriter.printAugmentedMatrix("Матрица после исключения переменной из остальных строк:", matrix, n, basis);
            OutputWriter.printOperation("В базис добавлена переменная x" + (col + 1));
            pivotRow++;
        }

        checkConsistency(pivotRow);

        if (pivotRow < m) {
            throw new InfeasibleProblemException(
                    "Не удалось построить полное опорное решение: ранг системы меньше числа ограничений"
            );
        }

        OutputWriter.printOperation("\nКанонический вид получен. Строим опорное решение по базисным переменным.");
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
