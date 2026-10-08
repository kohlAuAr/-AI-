package com.campus.business.recruitment;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "club_application", indexes = {
        @Index(name = "idx_application_user", columnList = "user_id,id"),
        @Index(name = "idx_application_club", columnList = "club_id,id")})
public class ClubApplication {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(name = "club_id", nullable = false)
    private Long clubId;
    @Column(nullable = false, length = 500)
    private String reason;
    @Column(nullable = false)
    private String status;
    // NULL releases the unique slot after rejection/withdrawal; history rows are retained.
    @Column(unique = true, length = 80)
    private String activeKey;
    @Column(nullable = false, length = 300)
    private String feedback;
    @Column(nullable = false)
    private Instant createdAt;
    private Long reviewedBy;
    private Instant reviewedAt;

    protected ClubApplication() {}
    public ClubApplication(Long userId, Long clubId, String reason) {
        this.userId = userId; this.clubId = clubId; this.reason = reason;
        this.status = "pending"; this.activeKey = userId + ":" + clubId;
        this.feedback = ""; this.createdAt = Instant.now();
    }
    public void review(boolean approved, String feedback, Long reviewer) {
        this.status = approved ? "approved" : "rejected"; this.feedback = feedback;
        this.reviewedBy = reviewer; this.reviewedAt = Instant.now();
        if (!approved) this.activeKey = null;
    }
    public void withdraw() { this.status = "withdrawn"; this.activeKey = null; }
    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getClubId() { return clubId; }
    public String getReason() { return reason; }
    public String getStatus() { return status; }
    public String getFeedback() { return feedback; }
    public Instant getCreatedAt() { return createdAt; }
    public Long getReviewedBy() { return reviewedBy; }
    public Instant getReviewedAt() { return reviewedAt; }
}
