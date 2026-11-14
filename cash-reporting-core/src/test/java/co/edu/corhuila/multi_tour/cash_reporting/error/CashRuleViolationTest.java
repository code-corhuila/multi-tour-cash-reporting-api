package co.edu.corhuila.multi_tour.cash_reporting.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class CashRuleViolationTest {

    @Test
    void theFamilyIsSealedAndHoldsTheTenErrorsOfThePlanAndTheOnePaymentRule() {
        assertThat(CashRuleViolation.class.isSealed()).isTrue();
        Set<String> names = Arrays.stream(CashRuleViolation.class.getPermittedSubclasses())
                .map(Class::getSimpleName)
                .collect(Collectors.toSet());
        assertThat(names)
                .containsExactlyInAnyOrder(
                        "AlreadyOpen",
                        "AlreadyClosed",
                        "NoOpenCash",
                        "PendingClosure",
                        "ReasonRequired",
                        "InvalidTransition",
                        "RuleViolation",
                        "NotFound",
                        "PaymentAlreadyRecorded",
                        "Forbidden");
    }

    @Test
    void aViolationCarriesItsMessageAndIsUnchecked() {
        CashRuleViolation violation = new CashRuleViolation.AlreadyClosed("The cash of 2026-10-14 is closed");
        assertThat(violation).isInstanceOf(RuntimeException.class).hasMessage("The cash of 2026-10-14 is closed");
    }
}
