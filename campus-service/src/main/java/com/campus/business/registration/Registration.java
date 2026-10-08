package com.campus.business.registration;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "activity_registration", uniqueConstraints = @UniqueConstraint(columnNames = {"activityId", "userId"}))
public class Registration {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long activityId;
    @Column(nullable = false)
    private Long userId;
    private String status;
    private LocalDateTime registeredAt;
    private LocalDateTime cancelledAt;
    private LocalDateTime checkedInAt;
    protected Registration() {}
    public Registration(Long activityId, Long userId) { this.activityId = activityId; this.userId = userId; activate(); }
    public void activate() { status = "REGISTERED"; registeredAt = LocalDateTime.now(); cancelledAt = null; }
    public void cancel() { status = "CANCELLED"; cancelledAt = LocalDateTime.now(); }
    public Long getId() { return id; }
    public Long getActivityId() { return activityId; }
    public Long getUserId() { return userId; }
    public String getStatus() { return status; }
    public LocalDateTime getRegisteredAt() { return registeredAt; }
    public LocalDateTime getCancelledAt() { return cancelledAt; }
    public LocalDateTime getCheckedInAt() { return checkedInAt; }
    public void checkIn() { checkedInAt = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS); }
}
