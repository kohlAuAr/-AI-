package com.campus.business.finance;

import com.campus.business.activity.*;
import com.campus.business.identity.*;
import com.campus.business.membership.MembershipRepository;
import java.math.BigDecimal;
import java.security.Principal;
import java.time.*;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class FinanceService {
    private final ActivityRepository activities;
    private final ExpenseRepository expenses;
    private final MembershipRepository memberships;
    private final IdentityService identity;
    public FinanceService(ActivityRepository activities, ExpenseRepository expenses, MembershipRepository memberships, IdentityService identity) {
        this.activities = activities; this.expenses = expenses; this.memberships = memberships; this.identity = identity;
    }
    public FinanceView report(Principal principal, Long id) {
        Activity a = find(id); requireManager(principal, a.getClubId()); return view(a);
    }
    public ClubReport clubReport(Principal principal, Long clubId) {
        requireManager(principal, clubId);
        List<FinanceView> rows = activities.findByClubIdOrderByIdDesc(clubId).stream().filter(a -> !a.getStatus().equals("SAMPLE")).map(this::view).toList();
        BigDecimal budgets = rows.stream().filter(r -> r.budget() != null).map(FinanceView::budget).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal spent = rows.stream().map(FinanceView::spent).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ClubReport(clubId, budgets, spent, rows.stream().filter(r -> r.budget() == null).count(), rows);
    }
    @Transactional
    public FinanceView budget(Principal principal, Long id, BigDecimal amount) {
        Activity a = locked(id); requireManager(principal, a.getClubId()); a.setBudget(amount); return view(a);
    }
    @Transactional
    public ExpenseView add(Principal principal, Long id, FinanceController.ExpenseRequest body) {
        Activity a = locked(id); Account manager = requireManager(principal, a.getClubId());
        Expense previous = expenses.findByActivityIdAndRequestId(id, body.requestId().toString()).orElse(null);
        if (previous != null) {
            if (previous.getAmount().compareTo(body.amount()) != 0 || !previous.getDescription().equals(body.description().trim()) || !previous.getOccurredOn().equals(body.occurredOn()))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "同一 requestId 不能对应不同支出内容");
            return expenseView(previous);
        }
        return expenseView(expenses.saveAndFlush(new Expense(id, body, manager.getId())));
    }
    @Transactional
    public ExpenseView voidEntry(Principal principal, Long id, String reason) {
        Long activityId = expenses.findActivityIdById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "支出记录不存在"));
        Activity a = locked(activityId); Account manager = requireManager(principal, a.getClubId());
        Expense e = expenses.findById(id).orElseThrow();
        if (!e.getStatus().equals("ACTIVE")) throw new ResponseStatusException(HttpStatus.CONFLICT, "记录已经作废");
        e.voidEntry(reason, manager.getId()); expenses.flush(); return expenseView(e);
    }
    private Activity find(Long id) {
        Activity a = activities.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "活动不存在"));
        if (a.getStatus().equals("SAMPLE")) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "示例占位活动不能记账");
        return a;
    }
    private Activity locked(Long id) {
        Activity a = activities.findLockedById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "活动不存在"));
        if (a.getStatus().equals("SAMPLE")) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "示例占位活动不能记账");
        return a;
    }
    private Account requireManager(Principal principal, Long clubId) {
        Account user = identity.current(principal);
        if (!memberships.existsByUserIdAndClubIdAndRole(user.getId(), clubId, "MANAGER"))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能查看或维护自己负责社团的经费");
        return user;
    }
    private FinanceView view(Activity a) {
        BigDecimal spent = expenses.totalSpent(a.getId());
        return new FinanceView(a.getId(), a.getTitle(), a.getBudget(), spent, a.getBudget() == null ? null : a.getBudget().subtract(spent),
                a.getBudget() != null && spent.compareTo(a.getBudget()) > 0, expenses.findByActivityIdOrderByIdDesc(a.getId()).stream().map(this::expenseView).toList());
    }
    private ExpenseView expenseView(Expense e) {
        return new ExpenseView(e.getId(), e.getActivityId(), e.getRequestId(), e.getAmount(), e.getDescription(), e.getOccurredOn(), e.getCreatedBy(), e.getCreatedAt(), e.getStatus(), e.getVoidReason(), e.getVoidedAt(), e.getVoidedBy());
    }
    public record ExpenseView(Long id, Long activityId, String requestId, BigDecimal amount, String description, LocalDate occurredOn,
                              Long createdBy, Instant createdAt, String status, String voidReason, Instant voidedAt, Long voidedBy) {}
    public record FinanceView(Long activityId, String title, BigDecimal budget, BigDecimal spent, BigDecimal remaining, boolean overBudget, List<ExpenseView> expenses) {}
    public record ClubReport(Long clubId, BigDecimal budgetTotal, BigDecimal spentTotal, long unbudgetedActivities, List<FinanceView> activities) {}
}
