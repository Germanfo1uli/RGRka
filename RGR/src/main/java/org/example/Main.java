import org.example.exception.InfeasibleProblemException;
import org.example.exception.InvalidInputException;
import org.example.exception.UnboundedProblemException;
import org.example.io.FileInputReader;
import org.example.io.IInputReader;
import org.example.io.OutputWriter;
import org.example.model.LinearProblem;
import org.example.model.Solution;
import org.example.solver.JordanGaussSolver;
import org.example.solver.SimplexSolver;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class Main {
    private static final String INPUT_DIRECTORY = "src/main/java/org/example/";
    private static final String DEFAULT_INPUT_FILE = INPUT_DIRECTORY + "input.txt";

    public static void main(String[] args) {
        configureConsoleEncoding();

        List<String> filenames = resolveInputFiles(args);
        for (int i = 0; i < filenames.size(); i++) {
            String filename = filenames.get(i);

            if (filenames.size() > 1) {
                OutputWriter.printSection("Файл: " + filename);
            }

            processFile(filename);

            if (i < filenames.size() - 1) {
                System.out.println();
            }
        }
    }

    private static List<String> resolveInputFiles(String[] args) {
        List<String> filenames = new ArrayList<>();

        if (args.length == 0) {
            filenames.add(DEFAULT_INPUT_FILE);
            filenames.add(INPUT_DIRECTORY + "input2.txt");
            filenames.add(INPUT_DIRECTORY + "input3.txt");
            filenames.add(INPUT_DIRECTORY + "input4.txt");
            return filenames;
        }

        for (String arg : args) {
            filenames.add(resolveSingleInputFile(arg));
        }

        return filenames;
    }

    private static String resolveSingleInputFile(String arg) {
        String trimmed = arg.trim();

        if (trimmed.matches("\\d+")) {
            return "1".equals(trimmed)
                    ? DEFAULT_INPUT_FILE
                    : INPUT_DIRECTORY + "input" + trimmed + ".txt";
        }

        if (trimmed.matches("input\\d*")) {
            return "input".equals(trimmed)
                    ? DEFAULT_INPUT_FILE
                    : INPUT_DIRECTORY + trimmed + ".txt";
        }

        return trimmed;
    }

    private static void processFile(String filename) {
        try {
            IInputReader reader = new FileInputReader();
            LinearProblem problem = reader.readProblem(filename);

            OutputWriter.printProblem(problem);

            OutputWriter.printSection("Этап 1: Метод Жордана-Гаусса");
            JordanGaussSolver gaussSolver = new JordanGaussSolver(problem);
            Solution initialSolution = gaussSolver.solve();
            OutputWriter.printSolution(initialSolution, "Опорное решение");

            OutputWriter.printSection("Этап 2: Симплекс-метод");
            SimplexSolver simplexSolver = new SimplexSolver(problem, initialSolution);
            Solution optimalSolution = simplexSolver.solve();

            OutputWriter.printSection("РЕЗУЛЬТАТ");
            OutputWriter.printSolution(optimalSolution, "Оптимальное решение");

        } catch (InfeasibleProblemException e) {
            System.err.println("Ошибка: задача несовместна - " + e.getMessage());
        } catch (UnboundedProblemException e) {
            System.err.println("Ошибка: задача неограничена - " + e.getMessage());
        } catch (InvalidInputException e) {
            System.err.println("Ошибка входных данных: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Непредвиденная ошибка: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void configureConsoleEncoding() {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));
    }
}
