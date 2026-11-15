package co.edu.corhuila.multi_tour.cash_reporting.usecase;

import co.edu.corhuila.multi_tour.cash_reporting.access.Actor;
import co.edu.corhuila.multi_tour.cash_reporting.error.CashRuleViolation;
import co.edu.corhuila.multi_tour.cash_reporting.movement.MovementType;
import co.edu.corhuila.multi_tour.cash_reporting.movement.RecordedMovement;
import co.edu.corhuila.multi_tour.cash_reporting.port.CashMovementRepository;
import java.util.Objects;

/**
 * The income the saga register-payment recorded for a payment (getInternalCashIncomeOfPayment, AC-WF2-11 of HU-WFL-002).
 * After an unknown outcome of its step 1 the saga asks whether the income exists, by the rule the cash already keeps:
 * one income per payment and tenant. A read: only a service, nothing changes; an income of another tenant is never seen.
 */
public final class GetCashIncomeOfPayment {

    private final CashMovementRepository movements;

    public GetCashIncomeOfPayment(CashMovementRepository movements) {
        this.movements = movements;
    }

    public RecordedMovement execute(Actor service, String tenantId, String paymentId) {
        CashGuards.requireService(service);
        return movements.findIncomeOfPayment(Objects.requireNonNull(tenantId, "tenantId"),
                        Objects.requireNonNull(paymentId, "paymentId"))
                .filter(movement -> movement.movement().type() == MovementType.INCOME)
                .orElseThrow(() -> new CashRuleViolation.NotFound("The payment has no income in the cash of the tenant"));
    }
}
