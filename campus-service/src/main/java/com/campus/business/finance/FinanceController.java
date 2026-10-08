package com.campus.business.finance;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/manage")
public class FinanceController {
    private final FinanceService finances;
    public FinanceController(FinanceService finances) { this.finances = finances; }
    @GetMapping("/activities/{id}/finance")
    public FinanceService.FinanceView report(Principal user, @PathVariable Long id) { return finances.report(user, id); }
    @GetMapping("/clubs/{clubId}/finance")
    public FinanceService.ClubReport clubReport(Principal user, @PathVariable Long clubId) { return finances.clubReport(user, clubId); }
    @PutMapping("/activities/{id}/budget")
    public FinanceService.FinanceView budget(Principal user, @PathVariable Long id, @Valid @RequestBody BudgetRequest body) { return finances.budget(user, id, body.amount()); }
    @PostMapping("/activities/{id}/expenses")
    public FinanceService.ExpenseView add(Principal user, @PathVariable Long id, @Valid @RequestBody ExpenseRequest body) { return finances.add(user, id, body); }
    @PostMapping("/expenses/{id}/void")
    public FinanceService.ExpenseView voidEntry(Principal user, @PathVariable Long id, @Valid @RequestBody VoidRequest body) { return finances.voidEntry(user, id, body.reason()); }
    public record BudgetRequest(@NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal amount) {}
    public record ExpenseRequest(@NotNull UUID requestId, @NotNull @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal amount,
                                 @NotBlank @Size(max = 200) String description, @NotNull @PastOrPresent LocalDate occurredOn) {}
    public record VoidRequest(@NotBlank @Size(max = 300) String reason) {}
}
