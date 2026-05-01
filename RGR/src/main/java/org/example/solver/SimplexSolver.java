package org.example.solver;

import org.example.exception.InfeasibleProblemException;
import org.example.exception.UnboundedProblemException;
import org.example.io.OutputWriter;
import org.example.model.Fraction;
import org.example.model.LinearProblem;
import org.example.model.Solution;
import org.example.model.Solution.SolutionType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class SimplexSolver implements ISolver {
    private static final int MAX_ITERATIONS = 10_000;

    private final LinearProblem problem;
    private final Solution initialSolution;
    private final int m;
    private final int n;

    private Fraction[][] tableau;
    private int[] basis;

    public SimplexSolver(LinearProblem problem, Solution initialSolution) {
        this.problem = Objects.requireNonNull(problem, "Задача не может быть null");
        this.initialSolution = Objects.requireNonNull(initialSolution, "Начальное решение не может быть null");
        this.m = problem.getM();
        this.n = problem.getN();
        this.basis = initialSolution.getBasis().clone();
    }

    @Override
    public Solution solve() throws InfeasibleProblemException, UnboundedProblemException {
        System.out.println("Начало симплекс-метода...");

        initializeCanonicalTableau();
        ensureFeasibleBasis();
        validateNoSignContradictions();
        OutputWriter.printSimplexTableau("Начальная симплекс-таблица:", tableau, basis, n);

        for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
            System.out.println("\n--- Итерация " + (iteration + 1) + " ---");

            Fraction[] reducedCosts = computeReducedCosts();
            OutputWriter.printReducedCosts(reducedCosts, basis);
            int enteringVar = findEnteringVariable(reducedCosts);

            if (enteringVar == -1) {
                System.out.println("Оптимальное решение найдено!");
                Solution solution = buildCurrentSolution();
                return enrichWithAlternateSolutions(solution, reducedCosts);
            }

            System.out.println("Входящая переменная: x" + (enteringVar + 1));

            int leavingRow = findLeavingRow(enteringVar);
            if (leavingRow == -1) {
                throw new UnboundedProblemException(
                        "Задача неограничена: переменную x" + (enteringVar + 1) + " можно увеличивать неограниченно"
                );
            }

            System.out.println("Выходящая переменная: x" + (basis[leavingRow] + 1));
            OutputWriter.printOperation(
                    "Разрешающий элемент: a[" + (leavingRow + 1) + "," + (enteringVar + 1) + "] = " +
                            tableau[leavingRow][enteringVar]
            );

            performPivot(leavingRow, enteringVar);
            basis[leavingRow] = enteringVar;
            validateNoSignContradictions();
            OutputWriter.printSimplexTableau("Таблица после пересчета:", tableau, basis, n);
        }

        throw new InfeasibleProblemException(
                "Достигнуто максимальное число итераций симплекс-метода (" + MAX_ITERATIONS + ")"
        );
    }

    private void initializeCanonicalTableau() throws InfeasibleProblemException {
        tableau = new Fraction[m][n + 1];
        Fraction[][] constraints = problem.getConstraints();
        Fraction[] rightSide = problem.getRightSide();

        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                tableau[i][j] = constraints[i][j];
            }
            tableau[i][n] = rightSide[i];
        }

        if (basis.length != m) {
            throw new InfeasibleProblemException(
                    "Начальное опорное решение неполно: ожидалось " + m + " базисных переменных, получено " + basis.length
            );
        }

        for (int row = 0; row < m; row++) {
            int basisVar = basis[row];
            if (basisVar < 0 || basisVar >= n) {
                throw new InfeasibleProblemException("Некорректный индекс базисной переменной: " + basisVar);
            }

            int pivotRow = findPivotRowForBasis(row, basisVar);
            if (pivotRow == -1) {
                throw new InfeasibleProblemException(
                        "Не удалось восстановить канонический вид для базисной переменной x" + (basisVar + 1)
                );
            }

            if (pivotRow != row) {
                swapRows(row, pivotRow);
            }

            normalizePivotRow(row, basisVar);
            eliminateColumn(row, basisVar);
        }
    }

    private int findPivotRowForBasis(int startRow, int basisVar) {
        for (int row = startRow; row < m; row++) {
            if (!tableau[row][basisVar].isZero()) {
                return row;
            }
        }
        return -1;
    }

    private void ensureFeasibleBasis() throws InfeasibleProblemException {
        for (int row = 0; row < m; row++) {
            if (tableau[row][n].isNegative()) {
                throw new InfeasibleProblemException(
                        "Начальное опорное решение недопустимо: базисная переменная x" +
                                (basis[row] + 1) + " имеет отрицательное значение " + tableau[row][n]
                );
            }
        }
    }

    private void validateNoSignContradictions() throws InfeasibleProblemException {
        for (int row = 0; row < m; row++) {
            boolean allNonNegative = true;
            boolean allNonPositive = true;

            for (int col = 0; col < n; col++) {
                if (tableau[row][col].isNegative()) {
                    allNonNegative = false;
                }
                if (tableau[row][col].isPositive()) {
                    allNonPositive = false;
                }
            }

            if (allNonNegative && tableau[row][n].isNegative()) {
                throw new InfeasibleProblemException(
                        "Во время решения найдено противоречие: в строке " + (row + 1) +
                                " левая часть не может быть отрицательной, а правая часть равна " + tableau[row][n]
                );
            }

            if (allNonPositive && tableau[row][n].isPositive()) {
                throw new InfeasibleProblemException(
                        "Во время решения найдено противоречие: в строке " + (row + 1) +
                                " левая часть не может быть положительной, а правая часть равна " + tableau[row][n]
                );
            }
        }
    }

    private Fraction[] computeReducedCosts() {
        Fraction[] reducedCosts = new Fraction[n];
        Fraction[] objective = problem.getCanonicalObjective();

        for (int col = 0; col < n; col++) {
            reducedCosts[col] = objective[col];
        }

        for (int row = 0; row < m; row++) {
            Fraction basisCost = objective[basis[row]];
            if (basisCost.isZero()) {
                continue;
            }

            for (int col = 0; col < n; col++) {
                reducedCosts[col] = reducedCosts[col].subtract(basisCost.multiply(tableau[row][col]));
            }
        }

        return reducedCosts;
    }

    private int findEnteringVariable(Fraction[] reducedCosts) {
        int enteringVar = -1;
        Fraction bestCost = Fraction.ZERO;

        for (int col = 0; col < n; col++) {
            if (isBasic(col)) {
                continue;
            }

            if (reducedCosts[col].compareTo(bestCost) > 0) {
                bestCost = reducedCosts[col];
                enteringVar = col;
            }
        }

        return enteringVar;
    }

    private boolean isBasic(int variable) {
        for (int basisVar : basis) {
            if (basisVar == variable) {
                return true;
            }
        }
        return false;
    }

    private int findLeavingRow(int enteringVar) {
        int leavingRow = -1;
        Fraction minRatio = null;

        for (int row = 0; row < m; row++) {
            Fraction coefficient = tableau[row][enteringVar];
            if (!coefficient.isPositive()) {
                continue;
            }

            Fraction ratio = tableau[row][n].divide(coefficient);
            if (ratio.isNegative()) {
                continue;
            }

            OutputWriter.printOperation(
                    "Отношение для строки R" + (row + 1) + ": " + tableau[row][n] + " / " + coefficient + " = " + ratio
            );

            if (minRatio == null || ratio.compareTo(minRatio) < 0) {
                minRatio = ratio;
                leavingRow = row;
            } else if (ratio.equals(minRatio) && basis[row] < basis[leavingRow]) {
                leavingRow = row;
            }
        }

        return leavingRow;
    }

    private void performPivot(int pivotRow, int pivotCol) {
        normalizePivotRow(pivotRow, pivotCol);
        eliminateColumn(pivotRow, pivotCol);
    }

    private void normalizePivotRow(int pivotRow, int pivotCol) {
        Fraction pivot = tableau[pivotRow][pivotCol];
        if (pivot.isZero()) {
            throw new ArithmeticException("Ведущий элемент равен нулю");
        }

        OutputWriter.printOperation(
                "Нормализуем строку R" + (pivotRow + 1) + " делением на " + pivot
        );

        for (int col = 0; col <= n; col++) {
            tableau[pivotRow][col] = tableau[pivotRow][col].divide(pivot);
        }
    }

    private void eliminateColumn(int pivotRow, int pivotCol) {
        for (int row = 0; row < m; row++) {
            if (row == pivotRow) {
                continue;
            }

            Fraction factor = tableau[row][pivotCol];
            if (factor.isZero()) {
                continue;
            }

            OutputWriter.printOperation(
                    "Обнуляем элемент в строке R" + (row + 1) + " с помощью коэффициента " + factor
            );

            for (int col = 0; col <= n; col++) {
                tableau[row][col] = tableau[row][col].subtract(factor.multiply(tableau[pivotRow][col]));
            }
        }
    }

    private void swapRows(int firstRow, int secondRow) {
        Fraction[] temp = tableau[firstRow];
        tableau[firstRow] = tableau[secondRow];
        tableau[secondRow] = temp;
    }

    private Solution buildCurrentSolution() {
        Fraction[] values = new Fraction[n];
        for (int i = 0; i < n; i++) {
            values[i] = Fraction.ZERO;
        }

        for (int row = 0; row < m; row++) {
            values[basis[row]] = tableau[row][n];
        }

        Fraction objectiveValue = computeObjectiveValue(values);
        return new Solution.Builder(values, objectiveValue, basis.clone()).build();
    }

    private Fraction computeObjectiveValue(Fraction[] values) {
        Fraction result = Fraction.ZERO;
        Fraction[] objective = problem.getObjective();

        for (int i = 0; i < n; i++) {
            result = result.add(objective[i].multiply(values[i]));
        }

        return result;
    }

    private Solution enrichWithAlternateSolutions(Solution solution, Fraction[] reducedCosts) {
        List<Integer> alternateEnteringVars = new ArrayList<>();
        for (int col = 0; col < n; col++) {
            if (!isBasic(col) && reducedCosts[col].isZero()) {
                alternateEnteringVars.add(col);
            }
        }

        if (alternateEnteringVars.isEmpty()) {
            return solution;
        }

        System.out.println("\nОбнаружены альтернативные оптимальные решения.");

        Solution.Builder builder = new Solution.Builder(
                solution.getValues(),
                solution.getObjectiveValue(),
                solution.getBasis()
        ).type(SolutionType.MULTIPLE_SOLUTIONS);

        Fraction[][] savedTableau = copyTableau();
        int[] savedBasis = basis.clone();

        for (int enteringVar : alternateEnteringVars) {
            restoreState(savedTableau, savedBasis);

            int leavingRow = findLeavingRow(enteringVar);
            if (leavingRow == -1) {
                continue;
            }

            performPivot(leavingRow, enteringVar);
            basis[leavingRow] = enteringVar;

            Solution alternateSolution = buildCurrentSolution();
            builder.addAlternateSolution(alternateSolution);
        }

        restoreState(savedTableau, savedBasis);
        builder.generalForm(buildGeneralForm(alternateEnteringVars));
        return builder.build();
    }

    private Fraction[][] copyTableau() {
        Fraction[][] copy = new Fraction[m][n + 1];
        for (int row = 0; row < m; row++) {
            copy[row] = Arrays.copyOf(tableau[row], tableau[row].length);
        }
        return copy;
    }

    private void restoreState(Fraction[][] savedTableau, int[] savedBasis) {
        for (int row = 0; row < m; row++) {
            System.arraycopy(savedTableau[row], 0, tableau[row], 0, savedTableau[row].length);
        }
        System.arraycopy(savedBasis, 0, basis, 0, savedBasis.length);
    }

    private String buildGeneralForm(List<Integer> freeVariables) {
        StringBuilder sb = new StringBuilder();

        for (int variable = 0; variable < n; variable++) {
            sb.append("x").append(variable + 1).append(" = ");

            int basicRow = findBasicRow(variable);
            if (basicRow != -1) {
                sb.append(tableau[basicRow][n]);
                for (int freeVariable : freeVariables) {
                    Fraction coefficient = tableau[basicRow][freeVariable];
                    if (!coefficient.isZero()) {
                        appendParametricTerm(sb, coefficient.negate(), "t" + (freeVariable + 1));
                    }
                }
            } else if (freeVariables.contains(variable)) {
                sb.append("t").append(variable + 1).append(", t").append(variable + 1).append(" >= 0");
            } else {
                sb.append("0");
            }

            sb.append("\n");
        }

        sb.append("Z = ").append(computeObjectiveValue(buildCurrentSolution().getValues()));
        return sb.toString();
    }

    private int findBasicRow(int variable) {
        for (int row = 0; row < basis.length; row++) {
            if (basis[row] == variable) {
                return row;
            }
        }
        return -1;
    }

    private void appendParametricTerm(StringBuilder sb, Fraction coefficient, String parameter) {
        if (coefficient.isPositive()) {
            sb.append(" + ");
        } else {
            sb.append(" - ");
            coefficient = coefficient.negate();
        }

        if (!coefficient.equals(Fraction.ONE)) {
            sb.append(coefficient).append("*");
        }
        sb.append(parameter);
    }
}
