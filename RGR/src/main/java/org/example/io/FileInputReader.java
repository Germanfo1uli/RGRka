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
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FileInputReader implements IInputReader {
    private static final Pattern TERM_PATTERN = Pattern.compile("([+-]?[^+-]+)");
    private static final Pattern OBJECTIVE_PATTERN = Pattern.compile("^(min|max)\\s+z\\s*=\\s*(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern CONSTRAINT_PATTERN = Pattern.compile("^(.*?)(<=|>=|=)(.*)$");
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("x(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern NON_NEGATIVE_LINE_PATTERN = Pattern.compile("^x\\d+(\\s*,\\s*x\\d+)*\\s*>=\\s*0$", Pattern.CASE_INSENSITIVE);

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
        List<String> lines = new ArrayList<>();
        String line;
        while ((line = reader.readLine()) != null) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                lines.add(trimmed);
            }
        }

        if (lines.isEmpty()) {
            throw new InvalidInputException("Файл пуст");
        }

        ParsedFormulaProblem parsedProblem = parseFormulaProblem(lines);
        return buildCanonicalProblem(parsedProblem);
    }

    private ParsedFormulaProblem parseFormulaProblem(List<String> lines) throws InvalidInputException {
        Matcher objectiveMatcher = OBJECTIVE_PATTERN.matcher(lines.get(0));
        if (!objectiveMatcher.matches()) {
            throw new InvalidInputException(
                    "Первая строка должна иметь вид 'max Z = ...' или 'min Z = ...'"
            );
        }

        ObjectiveType objectiveType = parseObjectiveType(objectiveMatcher.group(1));
        ParsedExpression objectiveExpression = parseExpression(objectiveMatcher.group(2), 1);

        List<ParsedConstraint> constraints = new ArrayList<>();
        int maxVariableIndex = objectiveExpression.maxVariableIndex;

        for (int i = 1; i < lines.size(); i++) {
            String currentLine = lines.get(i);
            if (NON_NEGATIVE_LINE_PATTERN.matcher(normalizeSpaces(currentLine)).matches()) {
                maxVariableIndex = Math.max(maxVariableIndex, findMaxVariableIndex(currentLine));
                continue;
            }

            ParsedConstraint constraint = parseConstraint(currentLine, i + 1);
            constraints.add(constraint);
            maxVariableIndex = Math.max(maxVariableIndex, constraint.expression.maxVariableIndex);
        }

        if (constraints.isEmpty()) {
            throw new InvalidInputException("Не найдено ни одного ограничения");
        }

        return new ParsedFormulaProblem(objectiveType, objectiveExpression, constraints, maxVariableIndex);
    }

    private LinearProblem buildCanonicalProblem(ParsedFormulaProblem parsedProblem) throws InvalidInputException {
        int originalVariableCount = parsedProblem.maxVariableIndex;
        int slackCount = 0;

        for (ParsedConstraint constraint : parsedProblem.constraints) {
            if (constraint.relation != Relation.EQUAL) {
                slackCount++;
            }
        }

        int totalVariableCount = originalVariableCount + slackCount;
        Fraction[][] constraints = new Fraction[parsedProblem.constraints.size()][totalVariableCount];
        Fraction[] rightSide = new Fraction[parsedProblem.constraints.size()];
        Fraction[] objective = new Fraction[totalVariableCount];

        for (int i = 0; i < totalVariableCount; i++) {
            objective[i] = Fraction.ZERO;
        }

        for (int i = 0; i < originalVariableCount; i++) {
            if (i < parsedProblem.objectiveExpression.coefficients.length) {
                objective[i] = parsedProblem.objectiveExpression.coefficients[i];
            }
        }

        int slackIndex = originalVariableCount;
        for (int row = 0; row < parsedProblem.constraints.size(); row++) {
            ParsedConstraint parsedConstraint = parsedProblem.constraints.get(row);
            Fraction[] rowValues = new Fraction[totalVariableCount];
            Arrays.fill(rowValues, Fraction.ZERO);

            for (int col = 0; col < originalVariableCount; col++) {
                if (col < parsedConstraint.expression.coefficients.length) {
                    rowValues[col] = parsedConstraint.expression.coefficients[col];
                }
            }

            if (parsedConstraint.relation == Relation.LESS_OR_EQUAL) {
                rowValues[slackIndex++] = Fraction.ONE;
            } else if (parsedConstraint.relation == Relation.GREATER_OR_EQUAL) {
                rowValues[slackIndex++] = Fraction.ONE.negate();
            }

            constraints[row] = rowValues;
            rightSide[row] = parsedConstraint.rightSide;
        }

        return new LinearProblem(constraints, rightSide, objective, parsedProblem.objectiveType);
    }

    private ParsedConstraint parseConstraint(String line, int lineNumber) throws InvalidInputException {
        Matcher matcher = CONSTRAINT_PATTERN.matcher(removeSpacesAroundOperators(line));
        if (!matcher.matches()) {
            throw new InvalidInputException(
                    "Строка " + lineNumber + " должна содержать ограничение вида '... <= ...', '... >= ...' или '... = ...'"
            );
        }

        ParsedExpression expression = parseExpression(matcher.group(1), lineNumber);
        Relation relation = parseRelation(matcher.group(2));
        Fraction rightSide = parseScalar(matcher.group(3), lineNumber);

        return new ParsedConstraint(expression, relation, rightSide);
    }

    private ParsedExpression parseExpression(String rawExpression, int lineNumber) throws InvalidInputException {
        String expression = rawExpression.replace(" ", "").toLowerCase(Locale.ROOT);
        if (expression.isEmpty()) {
            throw new InvalidInputException("Пустое выражение в строке " + lineNumber);
        }

        if (expression.charAt(0) != '+' && expression.charAt(0) != '-') {
            expression = "+" + expression;
        }

        Matcher matcher = TERM_PATTERN.matcher(expression);
        List<ParsedTerm> terms = new ArrayList<>();
        int maxVariableIndex = 0;

        while (matcher.find()) {
            String term = matcher.group(1);
            if (term == null || term.isBlank()) {
                continue;
            }

            ParsedTerm parsedTerm = parseTerm(term, lineNumber);
            terms.add(parsedTerm);
            maxVariableIndex = Math.max(maxVariableIndex, parsedTerm.variableIndex);
        }

        if (terms.isEmpty()) {
            throw new InvalidInputException("В строке " + lineNumber + " не найдено ни одного члена вида axN");
        }

        Fraction[] coefficients = new Fraction[maxVariableIndex];
        Arrays.fill(coefficients, Fraction.ZERO);

        for (ParsedTerm term : terms) {
            int index = term.variableIndex - 1;
            coefficients[index] = coefficients[index].add(term.coefficient);
        }

        return new ParsedExpression(coefficients, maxVariableIndex);
    }

    private ParsedTerm parseTerm(String term, int lineNumber) throws InvalidInputException {
        Matcher variableMatcher = VARIABLE_PATTERN.matcher(term);
        if (!variableMatcher.find()) {
            throw new InvalidInputException("В строке " + lineNumber + " найден некорректный член: " + term);
        }

        int variableIndex = Integer.parseInt(variableMatcher.group(1));
        String coefficientPart = term.substring(0, variableMatcher.start());
        if (coefficientPart.endsWith("*")) {
            coefficientPart = coefficientPart.substring(0, coefficientPart.length() - 1);
        }

        Fraction coefficient;
        if (coefficientPart.isEmpty() || "+".equals(coefficientPart)) {
            coefficient = Fraction.ONE;
        } else if ("-".equals(coefficientPart)) {
            coefficient = Fraction.ONE.negate();
        } else {
            coefficient = parseScalar(coefficientPart, lineNumber);
        }

        return new ParsedTerm(variableIndex, coefficient);
    }

    private Fraction parseScalar(String rawValue, int lineNumber) throws InvalidInputException {
        String value = rawValue.trim().replace(" ", "");
        if (value.isEmpty()) {
            throw new InvalidInputException("Пустое число в строке " + lineNumber);
        }

        try {
            if (value.contains("/")) {
                String[] parts = value.split("/");
                if (parts.length != 2) {
                    throw new InvalidInputException("Некорректная дробь в строке " + lineNumber + ": " + rawValue);
                }

                long numerator = Long.parseLong(parts[0]);
                long denominator = Long.parseLong(parts[1]);
                return new Fraction(numerator, denominator);
            }

            if (value.contains(".")) {
                return new Fraction(Double.parseDouble(value));
            }

            return new Fraction(Long.parseLong(value));
        } catch (NumberFormatException e) {
            throw new InvalidInputException("Не удалось прочитать число в строке " + lineNumber + ": " + rawValue, e);
        } catch (IllegalArgumentException e) {
            throw new InvalidInputException("Некорректное число в строке " + lineNumber + ": " + rawValue, e);
        }
    }

    private ObjectiveType parseObjectiveType(String token) throws InvalidInputException {
        String normalized = token.trim().toLowerCase(Locale.ROOT);
        if ("max".equals(normalized)) {
            return ObjectiveType.MAX;
        }
        if ("min".equals(normalized)) {
            return ObjectiveType.MIN;
        }
        throw new InvalidInputException("Тип целевой функции должен быть min или max, получено: " + token);
    }

    private Relation parseRelation(String token) {
        if ("<=".equals(token)) {
            return Relation.LESS_OR_EQUAL;
        }
        if (">=".equals(token)) {
            return Relation.GREATER_OR_EQUAL;
        }
        return Relation.EQUAL;
    }

    private int findMaxVariableIndex(String line) {
        Matcher matcher = VARIABLE_PATTERN.matcher(line);
        int maxIndex = 0;
        while (matcher.find()) {
            maxIndex = Math.max(maxIndex, Integer.parseInt(matcher.group(1)));
        }
        return maxIndex;
    }

    private String normalizeSpaces(String line) {
        return line.trim().replaceAll("\\s+", " ");
    }

    private String removeSpacesAroundOperators(String line) {
        return line.replaceAll("\\s*(<=|>=|=)\\s*", "$1");
    }

    private enum Relation {
        LESS_OR_EQUAL,
        GREATER_OR_EQUAL,
        EQUAL
    }

    private static final class ParsedFormulaProblem {
        private final ObjectiveType objectiveType;
        private final ParsedExpression objectiveExpression;
        private final List<ParsedConstraint> constraints;
        private final int maxVariableIndex;

        private ParsedFormulaProblem(
                ObjectiveType objectiveType,
                ParsedExpression objectiveExpression,
                List<ParsedConstraint> constraints,
                int maxVariableIndex
        ) {
            this.objectiveType = objectiveType;
            this.objectiveExpression = objectiveExpression;
            this.constraints = constraints;
            this.maxVariableIndex = maxVariableIndex;
        }
    }

    private static final class ParsedConstraint {
        private final ParsedExpression expression;
        private final Relation relation;
        private final Fraction rightSide;

        private ParsedConstraint(ParsedExpression expression, Relation relation, Fraction rightSide) {
            this.expression = expression;
            this.relation = relation;
            this.rightSide = rightSide;
        }
    }

    private static final class ParsedExpression {
        private final Fraction[] coefficients;
        private final int maxVariableIndex;

        private ParsedExpression(Fraction[] coefficients, int maxVariableIndex) {
            this.coefficients = coefficients;
            this.maxVariableIndex = maxVariableIndex;
        }
    }

    private static final class ParsedTerm {
        private final int variableIndex;
        private final Fraction coefficient;

        private ParsedTerm(int variableIndex, Fraction coefficient) {
            this.variableIndex = variableIndex;
            this.coefficient = coefficient;
        }
    }
}
