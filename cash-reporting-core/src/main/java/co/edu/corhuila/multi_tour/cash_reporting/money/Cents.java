package co.edu.corhuila.multi_tour.cash_reporting.money;

/** Money as a whole number of cents. Totals may be negative; an amount to register may not. */
public record Cents(long value) {

    public static final Cents ZERO = new Cents(0);

    public static Cents of(long value) {
        return new Cents(value);
    }

    /** An amount typed for a movement: it must be greater than zero (the movement type gives the direction). */
    public static Cents amount(long value) {
        if (value <= 0) {
            throw new IllegalArgumentException("An amount must be greater than 0");
        }
        return new Cents(value);
    }

    public Cents plus(Cents other) {
        return new Cents(Math.addExact(value, other.value));
    }

    public Cents minus(Cents other) {
        return new Cents(Math.subtractExact(value, other.value));
    }
}
