package com.campus.business.finance;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "activity_expense", uniqueConstraints = @UniqueConstraint(columnNames = {"activityId", "requestId"}))
public class Expense {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long activityId;
    @Column(nullable = false, length = 36)
    private String requestId;
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;
    @Column(nullable = false, length = 200)
    private String description;
    private LocalDate occurredOn;
    private Long createdBy;
    private Instant createdAt;
    private String status;
    @Column(length = 300)
    private String voidReason;
    private Instant voidedAt;
    private Long voidedBy;
    protected Expense() {}
    public Expense(Long activityId, FinanceController.ExpenseRequest body, Long userId) {
        this.activityId = activityId; this.requestId = body.requestId().toString(); this.amount = body.amount();
        this.description = body.description().trim(); this.occurredOn = body.occurredOn();
        this.createdBy = userId; this.createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS); this.status = "ACTIVE";
    }
    public void voidEntry(String reason, Long userId) { status = "VOID"; voidReason = reason.trim(); voidedBy = userId; voidedAt = Instant.now().truncatedTo(ChronoUnit.MICROS); }
    public Long getId() { return id; }
    public Long getActivityId() { return activityId; }
    public String getRequestId() { return requestId; }
    public BigDecimal getAmount() { return amount; }
    public String getDescription() { return description; }
    public LocalDate getOccurredOn() { return occurredOn; }
    public Long getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public String getStatus() { return status; }
    public String getVoidReason() { return voidReason; }
    public Instant getVoidedAt() { return voidedAt; }
    public Long getVoidedBy() { return voidedBy; }
}
