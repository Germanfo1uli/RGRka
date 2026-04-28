package org.example.model;

import org.example.exception.InvalidInputException;

import java.util.Arrays;

public final class LinearProblem {
    public enum ObjectiveType {
        MAX,
        MIN
    }

    private final Fraction[][] constraints;
    private final Fraction[] rightSide;
    private final Fraction[] objective;
    private final ObjectiveType objectiveType;
    private final int m;
    private final int n;

    public LinearProblem(Fraction[][] constraints, Fraction[] rightSide, Fraction[] objective)
            throws InvalidInputException {
        this(constraints, rightSide, objective, ObjectiveType.MAX);
    }

    public LinearProblem(
            Fraction[][] constraints,
            Fraction[] rightSide,
            Fraction[] objective,
            ObjectiveType objectiveType
    ) throws InvalidInputException {
        validateInput(constraints, rightSide, objective, objectiveType);

        this.m = constraints.length;
        this.n = constraints[0].length;
        this.constraints = deepCopyMatrix(constraints);
        this.rightSide = Arrays.copyOf(rightSide, rightSide.length);
        this.objective = Arrays.copyOf(objective, objective.length);
        this.objectiveType = objectiveType;
    }

    private void validateInput(
            Fraction[][] constraints,
            Fraction[] rightSide,
            Fraction[] objective,
            ObjectiveType objectiveType
    ) throws InvalidInputException {
        if (constraints == null || rightSide == null || objective == null || objectiveType == null) {
            throw new InvalidInputException("Входные данные не могут быть null");
        }

        if (constraints.length == 0) {
            throw new InvalidInputException("Должно быть хотя бы одно ограничение");
        }

        if (constraints[0].length == 0) {
            throw new InvalidInputException("Должна быть хотя бы одна переменная");
        }

        int expectedCols = constraints[0].length;
        for (int i = 0; i < constraints.length; i++) {
            if (constraints[i] == null) {
                throw new InvalidInputException("Строка " + i + " матрицы ограничений равна null");
            }
            if (constraints[i].length != expectedCols) {
                throw new InvalidInputException("Несовместимый размер строки " + i);
            }
            for (int j = 0; j < constraints[i].length; j++) {
                if (constraints[i][j] == null) {
                    throw new InvalidInputException(
                            "Элемент [" + i + "][" + j + "] матрицы ограничений равен null"
                    );
                }
            }
        }

        if (rightSide.length != constraints.length) {
            throw new InvalidInputException(
                    "Размер правой части (" + rightSide.length +
                            ") не совпадает с количеством ограничений (" + constraints.length + ")"
            );
        }

        for (int i = 0; i < rightSide.length; i++) {
            if (rightSide[i] == null) {
                throw new InvalidInputException("Элемент " + i + " правой части равен null");
            }
        }

        if (objective.length != expectedCols) {
            throw new InvalidInputException(
                    "Размер целевой функции (" + objective.length +
                            ") не совпадает с количеством переменных (" + expectedCols + ")"
            );
        }

        for (int i = 0; i < objective.length; i++) {
            if (objective[i] == null) {
                throw new InvalidInputException("Элемент " + i + " целевой функции равен null");
            }
        }
    }

    private Fraction[][] deepCopyMatrix(Fraction[][] matrix) {
        Fraction[][] copy = new Fraction[matrix.length][];
        for (int i = 0; i < matrix.length; i++) {
            copy[i] = Arrays.copyOf(matrix[i], matrix[i].length);
        }
        return copy;
    }

    public Fraction[][] getConstraints() {
        return deepCopyMatrix(constraints);
    }

    public Fraction[] getRightSide() {
        return Arrays.copyOf(rightSide, rightSide.length);
    }

    public Fraction[] getObjective() {
        return Arrays.copyOf(objective, objective.length);
    }

    public Fraction[] getCanonicalObjective() {
        Fraction[] canonical = Arrays.copyOf(objective, objective.length);
        if (objectiveType == ObjectiveType.MAX) {
            return canonical;
        }

        for (int i = 0; i < canonical.length; i++) {
            canonical[i] = canonical[i].negate();
        }
        return canonical;
    }

    public ObjectiveType getObjectiveType() {
        return objectiveType;
    }

    public int getM() {
        return m;
    }

    public int getN() {
        return n;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Ограничения:\n");

        for (int i = 0; i < m; i++) {
            appendExpression(sb, constraints[i]);
            sb.append(" = ").append(rightSide[i]).append("\n");
        }

        sb.append("\nЦелевая функция (")
                .append(objectiveType == ObjectiveType.MAX ? "максимизация" : "минимизация")
                .append("):\nZ = ");
        appendExpression(sb, objective);
        sb.append(" -> ").append(objectiveType.name().toLowerCase());

        return sb.toString();
    }

    private void appendExpression(StringBuilder sb, Fraction[] coefficients) {
        boolean first = true;

        for (int i = 0; i < coefficients.length; i++) {
            Fraction coefficient = coefficients[i];
            if (coefficient.isZero()) {
                continue;
            }

            if (!first) {
                sb.append(coefficient.isPositive() ? " + " : " - ");
            } else if (coefficient.isNegative()) {
                sb.append("-");
            }

            sb.append(coefficient.abs()).append("*x").append(i + 1);
            first = false;
        }

        if (first) {
            sb.append("0");
        }
    }
}
