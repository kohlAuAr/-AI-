package com.campus.business.activity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

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
