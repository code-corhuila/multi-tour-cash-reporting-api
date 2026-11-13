package co.edu.corhuila.multi_tour.cash_reporting.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * The contract gate (spec section 13, step 1): the last class to run. Every operation of the contract that belongs to
 * HU-CSH-001, and the health probe, must have been answered and validated by a C test; one that was not fails the build.
 */
@Order(Integer.MAX_VALUE)
class ContractGateTest {

    private static final String STORY = "HU-CSH-001";
    private static final String HEALTH = "getHealth";

    /** DD-1: operations of the stories HU-CSH-002 and HU-CSH-003; they leave this list when those stories are built. */
    static final Set<String> DD1_OTHER_STORIES = Set.of(
            "listMonthlyReports", "getMonthlyReportSummary", "listMonthlyConsolidations");

    @Test
    void everyOperationOfTheStoryIsExercised() {
        List<String> required = ExercisedOperations.contract().values().stream()
                .filter(operation -> operation.story().equals(STORY) || operation.id().equals(HEALTH))
                .map(ExercisedOperations.Operation::id)
                .filter(id -> !DD1_OTHER_STORIES.contains(id))
                .toList();

        assertThat(required).as("the operations the gate requires").hasSizeGreaterThanOrEqualTo(10);
        assertThat(ExercisedOperations.exercised()).as("operations answered by the C tests").containsAll(required);
    }

    @Test
    void theAllowlistsNameOnlyOperationsThatAreStillOutOfReach() {
        var contract = ExercisedOperations.contract();
        assertThat(contract.keySet()).containsAll(DD1_OTHER_STORIES);
        DD1_OTHER_STORIES.forEach(id -> assertThat(contract.get(id).story()).as(id).isNotEqualTo(STORY));
        assertThat(ExercisedOperations.exercised()).as("an exercised operation must leave its allowlist")
                .doesNotContainAnyElementsOf(DD1_OTHER_STORIES);
    }
}
