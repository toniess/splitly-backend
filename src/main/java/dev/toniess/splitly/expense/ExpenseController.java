package dev.toniess.splitly.expense;

import dev.toniess.splitly.expense.dto.CreateExpenseRequest;
import dev.toniess.splitly.expense.dto.ExpenseFeedPage;
import dev.toniess.splitly.expense.dto.ExpenseResponse;
import dev.toniess.splitly.expense.dto.UpdateExpenseRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestBody;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class ExpenseController {
    private final ExpenseService expenseService;

    public ExpenseController(ExpenseService expenseService) {
        this.expenseService = expenseService;
    }

    @PostMapping("/groups/{groupId}/expenses")
    @ResponseStatus(HttpStatus.CREATED)
    public ExpenseResponse createExpense(
            @AuthenticationPrincipal UUID actorId,
            @PathVariable UUID groupId,
            @RequestBody @Valid CreateExpenseRequest createExpenseRequest
    ) {
        return expenseService.create(actorId, groupId, createExpenseRequest);
    }

    @GetMapping("/expenses/{expenseId}")
    @ResponseStatus(HttpStatus.OK)
    public ExpenseResponse getExpense(
            @AuthenticationPrincipal UUID actorId,
            @PathVariable UUID expenseId
    ) {
        return expenseService.getById(actorId, expenseId);
    }

    @DeleteMapping("/expenses/{expenseId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteExpense(
            @AuthenticationPrincipal UUID actorId,
            @PathVariable UUID expenseId
    ) {
        expenseService.delete(actorId, expenseId);
    }

    @PutMapping("/expenses/{expenseId}")
    @ResponseStatus(HttpStatus.OK)
    public ExpenseResponse updateExpense(
            @AuthenticationPrincipal UUID actorId,
            @PathVariable UUID expenseId,
            @RequestBody @Valid UpdateExpenseRequest updateExpenseRequest
    ) {
        return expenseService.update(actorId, expenseId, updateExpenseRequest);
    }

    @GetMapping("/groups/{groupId}/expenses")
    public ExpenseFeedPage getSummary(
            @AuthenticationPrincipal UUID actorId,
            @PathVariable UUID groupId,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
            @RequestParam(required = false) String cursor
    ) {
        return expenseService.getFeed(actorId, groupId, cursor, limit);
    }


}
