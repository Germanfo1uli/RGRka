package org.example.io;

import org.example.model.Fraction;
import org.example.model.LinearProblem;
import org.example.model.Solution;

public class OutputWriter {

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

    private static void printSolutionValues(Solution solution) {
        Fraction[] values = solution.getValues();
        for (int i = 0; i < values.length; i++) {
            System.out.println("x" + (i + 1) + " = " + values[i] +
                    " (~ " + String.format("%.6f", values[i].toDouble()) + ")");
        }
        System.out.println("Z = " + solution.getObjectiveValue() +
                " (~ " + String.format("%.6f", solution.getObjectiveValue().toDouble()) + ")");
    }
}
