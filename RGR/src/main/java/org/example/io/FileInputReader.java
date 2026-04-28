package org.example.io;

import org.example.exception.InvalidInputException;
import org.example.model.Fraction;
import org.example.model.LinearProblem;
import org.example.model.LinearProblem.ObjectiveType;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class FileInputReader implements IInputReader {

    @Override
    public LinearProblem readProblem(String filename) throws IOException, InvalidInputException {
        File file = resolveInputFile(filename).toFile();

        if (!file.exists()) {
            throw new FileNotFoundException("Файл не найден: " + filename);
        }

        if (!file.canRead()) {
            throw new IOException("Нет прав на чтение файла: " + filename);
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            return parseProblem(reader);
        } catch (NumberFormatException e) {
            throw new InvalidInputException("Ошибка формата числа: " + e.getMessage(), e);
        } catch (ArrayIndexOutOfBoundsException e) {
            throw new InvalidInputException("Недостаточно данных в строке", e);
        }
    }

    private Path resolveInputFile(String filename) {
        Path directPath = Paths.get(filename);
        if (Files.exists(directPath)) {
            return directPath;
        }

        List<Path> candidates = new ArrayList<>();
        candidates.add(Paths.get("src", "main", "resources", filename));
        candidates.add(Paths.get("src", "main", "java", "org", "example", filename));

        for (Path candidate : candidates) {
            if (Files.exists(candidate)) {
                return candidate;
            }
        }

        return directPath;
    }

    private LinearProblem parseProblem(BufferedReader reader) throws IOException, InvalidInputException {
        String dimensionsLine = reader.readLine();
        if (dimensionsLine == null) {
            throw new InvalidInputException("Файл пуст");
        }

        String[] dimensions = dimensionsLine.trim().split("\\s+");
        if (dimensions.length != 2) {
            throw new InvalidInputException(
                    "Первая строка должна содержать два числа (m n), получено: " + dimensions.length
            );
        }

        int m;
        int n;
        try {
            m = Integer.parseInt(dimensions[0]);
            n = Integer.parseInt(dimensions[1]);
        } catch (NumberFormatException e) {
            throw new InvalidInputException("Неверный формат размеров: " + e.getMessage(), e);
        }

        if (m <= 0 || n <= 0) {
            throw new InvalidInputException("Размеры должны быть положительными: m=" + m + ", n=" + n);
        }

        if (m > 1000 || n > 1000) {
            throw new InvalidInputException("Размеры слишком большие: m=" + m + ", n=" + n + " (макс. 1000)");
        }

        Fraction[][] constraints = new Fraction[m][n];
        Fraction[] rightSide = new Fraction[m];

        for (int i = 0; i < m; i++) {
            String line = reader.readLine();
            if (line == null) {
                throw new InvalidInputException(
                        "Недостаточно строк для ограничений: ожидалось " + m + ", получено " + i
                );
            }

            String[] values = line.trim().split("\\s+");
            if (values.length != n + 1) {
                throw new InvalidInputException(
                        "Строка " + (i + 1) + " должна содержать " + (n + 1) +
                                " чисел, получено: " + values.length
                );
            }

            for (int j = 0; j < n; j++) {
                constraints[i][j] = parseFraction(values[j], i + 1, j + 1);
            }
            rightSide[i] = parseFraction(values[n], i + 1, n + 1);
        }

        String objectiveLine = reader.readLine();
        if (objectiveLine == null) {
            throw new InvalidInputException("Отсутствует строка целевой функции");
        }

        ParsedObjective parsedObjective = parseObjective(reader, objectiveLine, m, n);
        return new LinearProblem(constraints, rightSide, parsedObjective.coefficients, parsedObjective.type);
    }

    private ParsedObjective parseObjective(
            BufferedReader reader,
            String objectiveLine,
            int rowNumber,
            int variablesCount
    ) throws IOException, InvalidInputException {
        String[] tokens = objectiveLine.trim().split("\\s+");
        ObjectiveType type = null;

        if (tokens.length == variablesCount + 1) {
            type = parseObjectiveTypeToken(tokens[variablesCount], false);
            if (type != null) {
                Fraction[] objective = new Fraction[variablesCount];
                for (int i = 0; i < variablesCount; i++) {
                    objective[i] = parseFraction(tokens[i], rowNumber + 2, i + 1);
                }
                return new ParsedObjective(objective, type);
            }
        }

        if (tokens.length != variablesCount) {
            throw new InvalidInputException(
                    "Целевая функция должна содержать " + variablesCount +
                            " коэффициентов, либо " + variablesCount + " коэффициентов и тип min/max"
            );
        }

        Fraction[] objective = new Fraction[variablesCount];
        for (int i = 0; i < variablesCount; i++) {
            objective[i] = parseFraction(tokens[i], rowNumber + 2, i + 1);
        }

        reader.mark(4096);
        String typeLine = reader.readLine();

        if (typeLine != null && !typeLine.trim().isEmpty()) {
            ObjectiveType parsedType = parseObjectiveTypeToken(typeLine.trim(), true);
            if (parsedType != null) {
                type = parsedType;
            } else {
                reader.reset();
            }
        }

        if (type == null) {
            type = ObjectiveType.MAX;
        }

        return new ParsedObjective(objective, type);
    }

    private ObjectiveType parseObjectiveTypeToken(String token, boolean strict) throws InvalidInputException {
        String normalized = token.trim().toLowerCase();
        if ("max".equals(normalized)) {
            return ObjectiveType.MAX;
        }
        if ("min".equals(normalized)) {
            return ObjectiveType.MIN;
        }
        if (strict) {
            throw new InvalidInputException("Тип целевой функции должен быть min или max, получено: " + token);
        }
        return null;
    }

    private Fraction parseFraction(String str, int row, int col) throws InvalidInputException {
        try {
            str = str.trim();

            if (str.isEmpty()) {
                throw new InvalidInputException("Пустое значение в позиции [" + row + "][" + col + "]");
            }

            if (str.contains("/")) {
                String[] parts = str.split("/");
                if (parts.length != 2) {
                    throw new InvalidInputException(
                            "Неверный формат дроби в позиции [" + row + "][" + col + "]: " + str
                    );
                }

                long num = Long.parseLong(parts[0].trim());
                long den = Long.parseLong(parts[1].trim());

                if (den == 0) {
                    throw new InvalidInputException("Знаменатель равен 0 в позиции [" + row + "][" + col + "]");
                }

                return new Fraction(num, den);
            }

            double value = Double.parseDouble(str);
            if (Double.isNaN(value) || Double.isInfinite(value)) {
                throw new InvalidInputException(
                        "Недопустимое значение в позиции [" + row + "][" + col + "]: " + str
                );
            }

            return new Fraction(value);
        } catch (NumberFormatException e) {
            throw new InvalidInputException(
                    "Ошибка парсинга числа в позиции [" + row + "][" + col + "]: " + str, e
            );
        } catch (IllegalArgumentException e) {
            throw new InvalidInputException(
                    "Ошибка создания дроби в позиции [" + row + "][" + col + "]: " + e.getMessage(), e
            );
        }
    }

    private static final class ParsedObjective {
        private final Fraction[] coefficients;
        private final ObjectiveType type;

        private ParsedObjective(Fraction[] coefficients, ObjectiveType type) {
            this.coefficients = coefficients;
            this.type = type;
        }
    }
}
