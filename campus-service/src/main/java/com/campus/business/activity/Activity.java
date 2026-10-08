package com.campus.business.activity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.math.BigDecimal;

@Entity
@Table(name = "club_activity")
public class Activity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long clubId;
    private String title;
    private String location;
    private LocalDateTime startTime;
    private int capacity;
    private String status;
    private boolean demo;
    @Column(length = 2000)
    private String description;
    private LocalDateTime registrationDeadline;
    private Long createdBy;
    private LocalDateTime publishedAt;
    private LocalDateTime updatedAt;
    private Long updatedBy;
    private LocalDateTime cancelledAt;
    private Long cancelledBy;
    @Column(length = 300)
    private String cancelReason;
    @Column(columnDefinition = "boolean default false")
    private boolean checkInOpen;
    private String checkInCode;
    @Column(precision = 10, scale = 2)
    private BigDecimal budget;

    protected Activity() {}

    public Activity(Long clubId, String title, String location, LocalDateTime startTime, int capacity) {
        this.clubId = clubId;
        this.title = title;
        this.location = location;
        this.startTime = startTime;
        this.capacity = capacity;
        this.status = "SAMPLE";
        this.demo = true;
    }

    public Long getId() { return id; }
    public static Activity draft(Long clubId, String title, String description, String location,
                                 LocalDateTime startTime, LocalDateTime deadline, int capacity, Long creator) {
        Activity activity = new Activity(clubId, title.trim(), location.trim(), startTime, capacity);
        activity.description = description.trim(); activity.registrationDeadline = deadline;
        activity.createdBy = creator; activity.status = "DRAFT";
        return activity;
    }
    public void publish() { status = "PUBLISHED"; publishedAt = LocalDateTime.now(); }
    public void update(ActivityController.DraftRequest body, Long userId) {
        title = body.title().trim(); description = body.description().trim(); location = body.location().trim();
        startTime = body.startTime(); registrationDeadline = body.registrationDeadline(); capacity = body.capacity();
        updatedAt = LocalDateTime.now(); updatedBy = userId;
    }
    public void cancel(String reason, Long userId) {
        status = "CANCELLED"; cancelReason = reason.trim(); cancelledBy = userId; cancelledAt = LocalDateTime.now();
        closeCheckIn();
    }
    public void openCheckIn(String code) { checkInOpen = true; checkInCode = code; }
    public void closeCheckIn() { checkInOpen = false; checkInCode = null; }
    public boolean isCheckInOpen() { return checkInOpen; }
    public String getCheckInCode() { return checkInCode; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public Long getUpdatedBy() { return updatedBy; }
    public LocalDateTime getCancelledAt() { return cancelledAt; }
    public Long getCancelledBy() { return cancelledBy; }
    public String getCancelReason() { return cancelReason; }
    public BigDecimal getBudget() { return budget; }
    public void setBudget(BigDecimal budget) { this.budget = budget; }
    public String getDescription() { return description; }
    public LocalDateTime getRegistrationDeadline() { return registrationDeadline; }
    public Long getCreatedBy() { return createdBy; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public Long getClubId() { return clubId; }
    public String getTitle() { return title; }
    public String getLocation() { return location; }
    public LocalDateTime getStartTime() { return startTime; }
    public int getCapacity() { return capacity; }
    public String getStatus() { return status; }
    public boolean isDemo() { return demo; }
}
