package org.example.model;

import java.util.Objects;

public final class Fraction implements Comparable<Fraction> {
    private final long numerator;
    private final long denominator;

    public static final Fraction ZERO = new Fraction(0, 1);
    public static final Fraction ONE = new Fraction(1, 1);

    private Fraction(long numerator, long denominator, boolean normalized) {
        this.numerator = numerator;
        this.denominator = denominator;
    }

    public Fraction(long numerator, long denominator) {
        if (denominator == 0) {
            throw new IllegalArgumentException("Знаменатель не может быть равен 0");
        }

        if (numerator == 0) {
            this.numerator = 0;
            this.denominator = 1;
            return;
        }

        long gcd = gcd(Math.abs(numerator), Math.abs(denominator));
        long num = numerator / gcd;
        long den = denominator / gcd;

        if (den < 0) {
            num = -num;
            den = -den;
        }

        this.numerator = num;
        this.denominator = den;
    }

    public Fraction(long value) {
        this(value, 1);
    }

    public Fraction(double value) {
        this(value, 1e-10);
    }

    public Fraction(double value, double epsilon) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException("Невозможно преобразовать NaN или Infinity в дробь");
        }

        long[] result = doubleToFraction(value, epsilon);
        this.numerator = result[0];
        this.denominator = result[1];
    }

    private long[] doubleToFraction(double value, double epsilon) {
        long sign = value < 0 ? -1 : 1;
        value = Math.abs(value);

        long intPart = (long) value;
        double fracPart = value - intPart;

        if (fracPart < epsilon) {
            return new long[]{sign * intPart, 1};
        }

        long n0 = 0, n1 = 1, n2;
        long d0 = 1, d1 = 0, d2;
        double x = fracPart;

        for (int i = 0; i < 100; i++) {
            if (Math.abs(x) < epsilon) break;

            long a = (long) x;
            n2 = a * n1 + n0;
            d2 = a * d1 + d0;

            if (d2 == 0) break;

            if (Math.abs((double) n2 / d2 - fracPart) < epsilon) {
                long num = sign * (intPart * d2 + n2);
                return new long[]{num, d2};
            }

            double newX = 1.0 / (x - a);
            if (Double.isInfinite(newX) || Double.isNaN(newX)) break;
            x = newX;

            n0 = n1;
            n1 = n2;
            d0 = d1;
            d1 = d2;
        }

        long num = sign * (intPart * d1 + n1);
        return new long[]{num, d1};
    }

    private long gcd(long a, long b) {
        while (b != 0) {
            long temp = b;
            b = a % b;
            a = temp;
        }
        return a;
    }

    public Fraction add(Fraction other) {
        Objects.requireNonNull(other, "Аргумент не может быть null");

        try {
            long num = Math.addExact(
                    Math.multiplyExact(this.numerator, other.denominator),
                    Math.multiplyExact(other.numerator, this.denominator)
            );
            long den = Math.multiplyExact(this.denominator, other.denominator);
            return new Fraction(num, den);
        } catch (ArithmeticException e) {
            throw new ArithmeticException("Переполнение при сложении дробей");
        }
    }

    public Fraction subtract(Fraction other) {
        Objects.requireNonNull(other, "Аргумент не может быть null");
        return this.add(other.negate());
    }

    public Fraction multiply(Fraction other) {
        Objects.requireNonNull(other, "Аргумент не может быть null");

        try {
            long num = Math.multiplyExact(this.numerator, other.numerator);
            long den = Math.multiplyExact(this.denominator, other.denominator);
            return new Fraction(num, den);
        } catch (ArithmeticException e) {
            throw new ArithmeticException("Переполнение при умножении дробей");
        }
    }

    public Fraction divide(Fraction other) {
        Objects.requireNonNull(other, "Аргумент не может быть null");

        if (other.numerator == 0) {
            throw new ArithmeticException("Деление на ноль");
        }

        try {
            long num = Math.multiplyExact(this.numerator, other.denominator);
            long den = Math.multiplyExact(this.denominator, other.numerator);
            return new Fraction(num, den);
        } catch (ArithmeticException e) {
            throw new ArithmeticException("Переполнение при делении дробей");
        }
    }

    public Fraction negate() {
        return new Fraction(-this.numerator, this.denominator, true);
    }

    public Fraction abs() {
        return numerator >= 0 ? this : this.negate();
    }

    public double toDouble() {
        return (double) numerator / denominator;
    }

    public boolean isZero() {
        return numerator == 0;
    }

    public boolean isPositive() {
        return numerator > 0;
    }

    public boolean isNegative() {
        return numerator < 0;
    }

    @Override
    public int compareTo(Fraction other) {
        Objects.requireNonNull(other, "Аргумент не может быть null");

        try {
            long diff = Math.subtractExact(
                    Math.multiplyExact(this.numerator, other.denominator),
                    Math.multiplyExact(other.numerator, this.denominator)
            );
            return Long.compare(diff, 0);
        } catch (ArithmeticException e) {
            return Double.compare(this.toDouble(), other.toDouble());
        }
    }

    @Override
    public String toString() {
        if (denominator == 1) {
            return String.valueOf(numerator);
        }
        return numerator + "/" + denominator;
    }

    public String toMixedString() {
        if (Math.abs(numerator) < denominator) {
            return toString();
        }

        long wholePart = numerator / denominator;
        long remainder = Math.abs(numerator % denominator);

        if (remainder == 0) {
            return String.valueOf(wholePart);
        }

        return wholePart + " " + remainder + "/" + denominator;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Fraction)) return false;
        Fraction other = (Fraction) obj;
        return this.numerator == other.numerator &&
                this.denominator == other.denominator;
    }

    @Override
    public int hashCode() {
        return Objects.hash(numerator, denominator);
    }

    public long getNumerator() {
        return numerator;
    }

    public long getDenominator() {
        return denominator;
    }
}
