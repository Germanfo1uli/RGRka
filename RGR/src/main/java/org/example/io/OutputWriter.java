package org.example.io;

import org.example.model.Fraction;
import org.example.model.LinearProblem;
import org.example.model.Solution;

public class OutputWriter {
    private static final int CELL_WIDTH = 12;

    public static void printSection(String title) {
        System.out.println("\n" + "=".repeat(60));
        System.out.println(" ".repeat(Math.max(0, (60 - title.length()) / 2)) + title);
        System.out.println("=".repeat(60));
    }

    public static void printProblem(LinearProblem problem) {
        printSection("Задача линейного программирования");
        System.out.println(problem);
    }

    public static void printSolution(Solution solution, String title) {
        if (title != null && !title.isEmpty()) {
            printSection(title);
        }

        System.out.println(solution);

        if (solution.getType() == Solution.SolutionType.MULTIPLE_SOLUTIONS) {
            int count = 1;
            for (Solution alternate : solution.getAlternateSolutions()) {
                System.out.println("\n--- Альтернативное решение #" + (++count) + " ---");
                printSolutionValues(alternate);
            }
        }
    }

    public static void printAugmentedMatrix(String title, Fraction[][] matrix, int variableCount) {
        if (title != null && !title.isEmpty()) {
            System.out.println("\n" + title);
        }

        StringBuilder header = new StringBuilder(padRight("", 6));
        for (int col = 0; col < variableCount; col++) {
            header.append(padLeft("x" + (col + 1), CELL_WIDTH));
        }
        header.append(" |").append(padLeft("b", CELL_WIDTH - 2));
        System.out.println(header);
        System.out.println("-".repeat(header.length()));

        for (int row = 0; row < matrix.length; row++) {
            StringBuilder line = new StringBuilder(padRight("R" + (row + 1), 6));
            for (int col = 0; col < variableCount; col++) {
                line.append(padLeft(matrix[row][col].toString(), CELL_WIDTH));
            }
            line.append(" |").append(padLeft(matrix[row][variableCount].toString(), CELL_WIDTH - 2));
            System.out.println(line);
        }
    }

    public static void printSimplexTableau(String title, Fraction[][] tableau, int[] basis, int variableCount) {
        if (title != null && !title.isEmpty()) {
            System.out.println("\n" + title);
        }

        StringBuilder header = new StringBuilder(padRight("Базис", 8));
        for (int col = 0; col < variableCount; col++) {
            header.append(padLeft("x" + (col + 1), CELL_WIDTH));
        }
        header.append(" |").append(padLeft("b", CELL_WIDTH - 2));
        System.out.println(header);
        System.out.println("-".repeat(header.length()));

        for (int row = 0; row < tableau.length; row++) {
            String basisLabel = basis != null && row < basis.length ? "x" + (basis[row] + 1) : "R" + (row + 1);
            StringBuilder line = new StringBuilder(padRight(basisLabel, 8));
            for (int col = 0; col < variableCount; col++) {
                line.append(padLeft(tableau[row][col].toString(), CELL_WIDTH));
            }
            line.append(" |").append(padLeft(tableau[row][variableCount].toString(), CELL_WIDTH - 2));
            System.out.println(line);
        }
    }

    public static void printReducedCosts(Fraction[] reducedCosts, int[] basis) {
        System.out.println("\nОценки переменных:");
        for (int col = 0; col < reducedCosts.length; col++) {
            boolean basic = false;
            for (int basisVar : basis) {
                if (basisVar == col) {
                    basic = true;
                    break;
                }
            }

            String suffix = basic ? " (базисная)" : "";
            System.out.println("Δx" + (col + 1) + " = " + reducedCosts[col] + suffix);
        }
    }

    public static void printOperation(String description) {
        System.out.println(description);
    }

    private static void printSolutionValues(Solution solution) {
        Fraction[] values = solution.getValues();
        for (int i = 0; i < values.length; i++) {
            System.out.println("x" + (i + 1) + " = " + values[i] +
                    " (~ " + String.format("%.6f", values[i].toDouble()) + ")");
        }
        System.out.println("Z = " + solution.getObjectiveValue() +
                " (~ " + String.format("%.6f", solution.getObjectiveValue().toDouble()) + ")");
    }

    private static String padLeft(String value, int width) {
        return String.format("%" + width + "s", value);
    }

    private static String padRight(String value, int width) {
        return String.format("%-" + width + "s", value);
    }
}
