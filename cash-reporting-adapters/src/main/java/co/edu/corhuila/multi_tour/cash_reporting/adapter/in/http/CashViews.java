package co.edu.corhuila.multi_tour.cash_reporting.adapter.in.http;

import co.edu.corhuila.multi_tour.cash_reporting.calendar.BusinessDate;
import co.edu.corhuila.multi_tour.cash_reporting.closure.CashClosure;
import co.edu.corhuila.multi_tour.cash_reporting.money.Cents;
import co.edu.corhuila.multi_tour.cash_reporting.money.DayView;
import co.edu.corhuila.multi_tour.cash_reporting.movement.RecordedMovement;
import co.edu.corhuila.multi_tour.cash_reporting.port.Page;
import co.edu.corhuila.multi_tour.cash_reporting.session.CashCorrection;
import co.edu.corhuila.multi_tour.cash_reporting.session.CashReopening;
import co.edu.corhuila.multi_tour.cash_reporting.session.CashSession;
import co.edu.corhuila.multi_tour.cash_reporting.session.ClosingMode;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.CashDay;
import co.edu.corhuila.multi_tour.cash_reporting.usecase.ClosureEntry;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

/** The JSON of the reads, field by field as the contract names them. The adapter only translates: no figure is computed here. */
final class CashViews {
    // Only the id of the user is stored with a movement or a closure; the role label is NO DEFINIDO until the session names it (G-USR).
    private static final String ROLE_LABEL = "Usuario del tenant";

    private CashViews() {}

    record Meta(int page, int limit, long total, int totalPages) {}

    record Listing<T>(List<T> data, Meta meta) {
        static <S, T> Listing<T> of(Page<S> page, Function<S, T> view) {
            return new Listing<>(page.items().stream().map(view).toList(),
                    new Meta(page.page(), page.limit(), page.total(), page.totalPages()));
        }
    }

    record Who(String userId, String roleLabel, String displayName) {}

    record DayResponse(String businessDate, String timeZone, String cashSessionId, String status, String origin,
            boolean hasOperationalOpening, boolean pendingClosure, @JsonInclude(NON_NULL) Long openingBaseCents, @JsonInclude(NON_NULL) Long parametrizedBaseCents, String openedAt,
            @JsonInclude(NON_NULL) Who openedBy,
            long incomeCents, long operationalPaymentsCents, long expensesCents, long refundsCents, long totalCents,
            @JsonInclude(NON_NULL) Long expectedCashCents, long netTransfersCents, String closedAt,
            @JsonInclude(NON_NULL) Who closedBy, String closingMode,
            boolean formalClosureOfPending) {}

    record MovementResponse(String cashMovementId, String businessDate, String movementType, String paymentMedium,
            String concept, long amountCents, boolean afterClosing, String reservationId, String registeredAt,
            Who registeredBy) {}

    record ReopeningResponse(Who reopenedBy, String reopenedAt, String reason) {}

    record CorrectionResponse(String correctionId, String businessDate, String justification, Who registeredBy,
            String registeredAt) {}

    record ClosureResponse(String businessDate, String badge, Long baseCents, long incomeCents,
            long operationalPaymentsCents, long expensesCents, long refundsCents, long totalCents, long entriesCashCents,
            long entriesTransferCents, long exitsCashCents, long exitsTransferCents, @JsonInclude(NON_NULL) Long expectedCashCents,
            String origin, boolean hasOperationalOpening, String closingMode, boolean formalClosureOfPending,
            @JsonInclude(NON_NULL) Who closedBy, String closedAt, List<ReopeningResponse> reopenings, List<CorrectionResponse> corrections) {}

    static DayResponse day(CashDay day) {
        CashSession session = day.session();
        CashClosure closure = day.closure();
        DayView view = day.figures();
        boolean opened = session != null && session.hasOperationalOpening();
        return new DayResponse(date(day.date()), day.zone().getId(), session == null ? null : sessionId(session),
                day.status().name(), session == null ? null : session.origin().name(), opened, day.pendingClosure(),
                value(view.openingBaseCents()), value(day.parametrizedBase()), opened ? session.opening().at().toString() : null,
                opened ? who(session.opening().by()) : null, view.incomeCents().value(),
                view.operationalPaymentsCents().value(), view.expensesCents().value(), view.refundsCents().value(),
                view.totalCents().value(), value(view.expectedCashCents()), view.netTransfersCents().value(),
                closure == null ? null : closure.closedAt().toString(), closure == null ? null : closer(closure),
                closure == null ? null : mode(closure.mode()), closure != null && closure.formalClosureOfPending());
    }

    static MovementResponse movement(RecordedMovement row) {
        return new MovementResponse(row.id(), date(row.date()), row.movement().type().name(),
                row.movement().medium().name(), row.concept(), row.movement().amount().value(), row.afterClosing(),
                row.refund() == null ? null : row.refund().reservationId(), row.registeredAt().toString(),
                who(row.registeredBy()));
    }

    static ClosureResponse closure(ClosureEntry entry) {
        CashClosure closure = entry.closure();
        DayView view = closure.view();
        return new ClosureResponse(date(closure.date()), entry.badge().name(), value(view.openingBaseCents()),
                view.incomeCents().value(), view.operationalPaymentsCents().value(), view.expensesCents().value(),
                view.refundsCents().value(), view.totalCents().value(), view.cashEntriesCents().value(),
                view.transferEntriesCents().value(), view.cashExitsCents().value(), view.transferExitsCents().value(),
                value(view.expectedCashCents()), closure.origin().name(), view.openingBaseCents() != null,
                mode(closure.mode()), closure.formalClosureOfPending(), closer(closure), closure.closedAt().toString(),
                entry.reopenings().stream().map(CashViews::reopening).toList(),
                entry.corrections().stream().map(CashViews::correction).toList());
    }

    private static ReopeningResponse reopening(CashReopening row) {
        return new ReopeningResponse(who(row.reopenedBy()), row.reopenedAt().toString(), row.reason());
    }

    static CorrectionResponse correction(CashCorrection row) {
        return new CorrectionResponse(row.id(), date(row.date()), row.justification(), who(row.registeredBy()),
                row.registeredAt().toString());
    }

    private static String date(BusinessDate date) {
        return date.value().toString();
    }

    private static Long value(Cents cents) {
        return cents == null ? null : cents.value();
    }

    private static Who who(String userId) {
        return new Who(userId, ROLE_LABEL, null);
    }

    // An automatic closing has no user: the portal shows "Cierre automatico (Multitour)".
    private static Who closer(CashClosure closure) {
        return closure.mode() == ClosingMode.AUTOMATIC ? null : who(closure.closedBy());
    }

    private static String mode(ClosingMode mode) {
        return mode == ClosingMode.AUTOMATIC ? "AUTOMATIC" : "FORMAL";
    }

    // The core names a cash by tenant and date; the contract wants an id, so this one is derived from both and is stable.
    private static String sessionId(CashSession session) {
        return UUID.nameUUIDFromBytes((session.tenantId() + "|" + session.date().value()).getBytes(StandardCharsets.UTF_8))
                .toString();
    }
}
