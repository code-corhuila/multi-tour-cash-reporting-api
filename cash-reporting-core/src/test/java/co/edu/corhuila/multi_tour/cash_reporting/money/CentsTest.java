package co.edu.corhuila.multi_tour.cash_reporting.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.cash_reporting.movement.CashMovement;
import co.edu.corhuila.multi_tour.cash_reporting.movement.MovementType;
import co.edu.corhuila.multi_tour.cash_reporting.movement.PaymentMedium;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class CentsTest {

    @Test
    void moneyIsAnIntegerOfCents() throws IOException {
        assertThat(Cents.of(129_000_000L).value()).isEqualTo(129_000_000L);
        assertThat(Cents.of(1).plus(Cents.of(2))).isEqualTo(Cents.of(3));
        assertThat(Cents.of(5).minus(Cents.of(8))).isEqualTo(Cents.of(-3));
        assertThat(Cents.ZERO).isEqualTo(Cents.of(0));
        assertThat(Cents.class.getRecordComponents())
                .singleElement()
                .satisfies(component -> assertThat(component.getType()).isEqualTo(long.class));

        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                assertThat(Files.readString(file))
                        .as("fractional type in %s", file)
                        .doesNotContainPattern("\\b(double|float|Double|Float|BigDecimal)\\b");
            }
        }
    }

    @Test
    void anAmountMustBeGreaterThanZero() {
        assertThat(Cents.amount(1).value()).isEqualTo(1L);
        assertThatThrownBy(() -> Cents.amount(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Cents.amount(-500)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CashMovement(MovementType.INCOME, PaymentMedium.CASH, Cents.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
