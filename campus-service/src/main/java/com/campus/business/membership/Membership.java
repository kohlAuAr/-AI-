package com.campus.business.membership;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "club_membership", uniqueConstraints = @UniqueConstraint(name = "uq_member_user_club", columnNames = {"user_id", "club_id"}), indexes = @Index(name = "idx_membership_club", columnList = "club_id"))
public class Membership {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(name = "club_id", nullable = false)
    private Long clubId;
    @Column(nullable = false)
    private String role;
    @Column(nullable = false)
    private Instant joinedAt;

    protected Membership() {}
    public Membership(Long userId, Long clubId, String role) {
        this.userId = userId; this.clubId = clubId; this.role = role; this.joinedAt = Instant.now();
    }
    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getClubId() { return clubId; }
    public String getRole() { return role; }
    public Instant getJoinedAt() { return joinedAt; }
}
