package co.edu.corhuila.multi_tour.cash_reporting.usecase;

/** Names of the events the use cases append to the outbox. */
final class CashEvents {

    static final String CASH_SESSION_OPENED = "CashSessionOpened";
    static final String CASH_SESSION_CLOSED = "CashSessionClosed";
    static final String CASH_SESSION_AUTO_CREATED = "CashSessionAutoCreated";
    static final String CASH_INCOME_RECORDED = "CashIncomeRecorded";
    static final String OPERATIONAL_PAYMENT_RECORDED = "OperationalPaymentRecorded";
    static final String OPERATIONAL_EXPENSE_RECORDED = "OperationalExpenseRecorded";
    static final String REFUND_EXECUTED = "RefundExecuted";

    private CashEvents() {}
}
