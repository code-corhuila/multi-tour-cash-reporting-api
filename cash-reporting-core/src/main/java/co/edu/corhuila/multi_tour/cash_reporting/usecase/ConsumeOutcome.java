package co.edu.corhuila.multi_tour.cash_reporting.usecase;

/** What the consumer did with an event: it applied it (an older state is recorded but changes nothing) or it was a repeat. */
public enum ConsumeOutcome {
    APPLIED,
    DUPLICATE
}
