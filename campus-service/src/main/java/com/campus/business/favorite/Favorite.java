package com.campus.business.favorite;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "club_favorite", uniqueConstraints = @UniqueConstraint(columnNames = {"userId", "clubId"}))
public class Favorite {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long userId;
    @Column(nullable = false)
    private Long clubId;
    private Instant createdAt;
    protected Favorite() {}
    public Favorite(Long userId, Long clubId) { this.userId = userId; this.clubId = clubId; this.createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS); }
    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getClubId() { return clubId; }
    public Instant getCreatedAt() { return createdAt; }
}
