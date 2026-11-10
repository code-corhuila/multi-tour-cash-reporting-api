package co.edu.corhuila.multi_tour.cash_reporting.usecase;

/** Names of the events the use cases append to the outbox. */
final class CashEvents {

    static final String CASH_SESSION_OPENED = "CashSessionOpened";
    static final String CASH_SESSION_CLOSED = "CashSessionClosed";
    static final String CASH_SESSION_AUTO_CLOSED = "CashSessionAutoClosed";
    static final String CASH_SESSION_REOPENED = "CashSessionReopened";
    static final String CASH_SESSION_AUTO_CREATED = "CashSessionAutoCreated";
    static final String CASH_INCOME_RECORDED = "CashIncomeRecorded";
    static final String OPERATIONAL_PAYMENT_RECORDED = "OperationalPaymentRecorded";
    static final String OPERATIONAL_EXPENSE_RECORDED = "OperationalExpenseRecorded";
    static final String REFUND_EXECUTED = "RefundExecuted";

    /** The totals of a day as 02-domain/domain-events.md asks for the closings: cents only, expected cash when there is a base. */
    static void putTotals(java.util.Map<String, Object> payload, co.edu.corhuila.multi_tour.cash_reporting.money.DayView view) {
        payload.put("incomeCents", view.incomeCents().value());
        payload.put("operationalPaymentsCents", view.operationalPaymentsCents().value());
        payload.put("expensesCents", view.expensesCents().value());
        payload.put("refundsCents", view.refundsCents().value());
        payload.put("totalCents", view.totalCents().value());
        if (view.expectedCashCents() != null) {
            payload.put("expectedCashCents", view.expectedCashCents().value());
        }
    }

    private CashEvents() {}
}
