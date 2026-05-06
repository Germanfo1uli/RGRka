package org.example.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class Solution {
    private final Fraction[] values;
    private final Fraction objectiveValue;
    private final int[] basis;
    private final SolutionType type;
    private final List<Solution> alternateSolutions;
    private final String generalForm;

    public enum SolutionType {
        OPTIMAL,
        UNBOUNDED,
        MULTIPLE_SOLUTIONS
    }

    private Solution(Builder builder) {
        this.values = Arrays.copyOf(builder.values, builder.values.length);
        this.objectiveValue = builder.objectiveValue;
        this.basis = Arrays.copyOf(builder.basis, builder.basis.length);
        this.type = builder.type;
        this.alternateSolutions = new ArrayList<>(builder.alternateSolutions);
        this.generalForm = builder.generalForm;
    }

    public static class Builder {
        private Fraction[] values;
        private Fraction objectiveValue;
        private int[] basis;
        private SolutionType type = SolutionType.OPTIMAL;
        private List<Solution> alternateSolutions = new ArrayList<>();
        private String generalForm = "";

        public Builder(Fraction[] values, Fraction objectiveValue, int[] basis) {
            Objects.requireNonNull(values, "Значения переменных не могут быть null");
            Objects.requireNonNull(objectiveValue, "Значение целевой функции не может быть null");
            Objects.requireNonNull(basis, "Базис не может быть null");

            this.values = values;
            this.objectiveValue = objectiveValue;
            this.basis = basis;
        }

        public Builder type(SolutionType type) {
            this.type = Objects.requireNonNull(type);
            return this;
        }

        public Builder addAlternateSolution(Solution solution) {
            this.alternateSolutions.add(Objects.requireNonNull(solution));
            return this;
        }

        public Builder generalForm(String generalForm) {
            this.generalForm = Objects.requireNonNull(generalForm);
            return this;
        }

        public Solution build() {
            return new Solution(this);
        }
    }

    public Fraction[] getValues() {
        return Arrays.copyOf(values, values.length);
    }

    public Fraction getObjectiveValue() {
        return objectiveValue;
    }

    public int[] getBasis() {
        return Arrays.copyOf(basis, basis.length);
    }

    public SolutionType getType() {
        return type;
    }

    public List<Solution> getAlternateSolutions() {
        return new ArrayList<>(alternateSolutions);
    }

    public String getGeneralForm() {
        return generalForm;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        if (type == SolutionType.UNBOUNDED) {
            sb.append("Задача неограничена (целевая функция не ограничена сверху).\n");
            return sb.toString();
        }

        sb.append("Решение:\n");
        for (int i = 0; i < values.length; i++) {
            sb.append("x").append(i + 1).append(" = ")
                    .append(values[i])
                    .append("\n");
        }

        sb.append("Z = ").append(objectiveValue).append("\n");

        sb.append("Базисные переменные: ");
        for (int i = 0; i < basis.length; i++) {
            sb.append("x").append(basis[i] + 1);
            if (i < basis.length - 1) {
                sb.append(", ");
            }
        }
        sb.append("\n");

        if (type == SolutionType.MULTIPLE_SOLUTIONS) {
            sb.append("\n!!! Задача имеет бесконечно много решений !!!\n");

            if (!generalForm.isEmpty()) {
                sb.append("\nОбщий вид решений:\n").append(generalForm);
                if (!generalForm.endsWith("\n")) {
                    sb.append("\n");
                }
            }

            if (!alternateSolutions.isEmpty()) {
                sb.append("Найдено альтернативных решений: ")
                        .append(alternateSolutions.size() + 1).append("\n");
            }
        }

        return sb.toString();
    }
}
