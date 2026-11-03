package co.edu.corhuila.multi_tour.cash_reporting.app;

import co.edu.corhuila.multi_tour.cash_reporting.access.Actor;
import co.edu.corhuila.multi_tour.cash_reporting.access.CashPermissions;
import co.edu.corhuila.multi_tour.cash_reporting.access.Operation;
import co.edu.corhuila.multi_tour.cash_reporting.adapter.in.http.TokenFilter;
import co.edu.corhuila.multi_tour.cash_reporting.error.CashRuleViolation;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Routes that exist only in the tests: they let the filter and the error mapping be seen without a business controller. */
@RestController
@RequestMapping("/api/v1/cash-reporting/_probe")
class ProbeController {

    record Echo(String concept) {
    }

    private final CashPermissions permissions;

    ProbeController(CashPermissions permissions) {
        this.permissions = permissions;
    }

    @GetMapping("/actor")
    Map<String, Object> actor(HttpServletRequest request) {
        Actor actor = TokenFilter.actorOf(request);
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("tenantId", actor.tenantId());
        view.put("userId", actor.userId());
        view.put("roles", new TreeSet<>(actor.roles()));
        view.put("permissions", new TreeSet<>(actor.permissions()));
        view.put("canOpenTheCash", permissions.allows(actor, Operation.OPEN_CASH_DAY));
        return view;
    }

    @GetMapping("/mdc")
    Map<String, String> mdc() {
        return Map.of("traceId", String.valueOf(MDC.get("traceId")));
    }

    @GetMapping("/violation/{kind}")
    void violation(@PathVariable String kind) {
        throw switch (kind) {
            case "already-open" -> new CashRuleViolation.AlreadyOpen("The cash of 2026-11-03 is already open");
            case "pending" -> new CashRuleViolation.PendingClosure("The cash of 2026-08-31 is pending closure");
            default -> new IllegalStateException("unknown kind " + kind);
        };
    }

    @GetMapping("/boom")
    void boom() {
        throw new IllegalStateException("jdbc:postgresql://db.internal:5432/cash password=secret");
    }

    @PostMapping("/echo")
    Echo echo(@RequestBody Echo body) {
        return body;
    }
}
