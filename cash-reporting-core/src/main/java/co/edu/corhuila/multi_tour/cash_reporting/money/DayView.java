package co.edu.corhuila.multi_tour.cash_reporting.money;

/**
 * The computed figures of a day. {@code openingBaseCents} and {@code expectedCashCents} are null when the day has
 * no operational opening; the total still counts the base as zero.
 */
public record DayView(
        Cents openingBaseCents,
        Cents incomeCents,
        Cents operationalPaymentsCents,
        Cents expensesCents,
        Cents refundsCents,
        Cents totalCents,
        Cents cashEntriesCents,
        Cents cashExitsCents,
        Cents transferEntriesCents,
        Cents transferExitsCents,
        Cents expectedCashCents,
        Cents netTransfersCents) {}
