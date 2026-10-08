package com.campus.business.identity;

import jakarta.persistence.*;

@Entity
@Table(name = "campus_account")
public class Account {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 64)
    private String username;
    @Column(nullable = false)
    private String passwordHash;
    @Column(nullable = false)
    private String name;
    private String major;
    @Column(nullable = false)
    private String role;
    @Column(length = 300)
    private String interestTags;
    @Column(length = 1000)
    private String interestDescription;
    @Column(length = 200)
    private String availableTime;

    protected Account() {}
    public Account(String username, String passwordHash, String name, String major, String role) {
        this.username = username; this.passwordHash = passwordHash; this.name = name; this.major = major; this.role = role;
    }
    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getName() { return name; }
    public String getMajor() { return major; }
    public String getRole() { return role; }
    public String getInterestTags() { return interestTags == null ? "" : interestTags; }
    public String getAvailableTime() { return availableTime == null ? "" : availableTime; }
    public String getInterestDescription() { return interestDescription == null ? "" : interestDescription; }
    public void setInterestDescription(String description) { this.interestDescription = description; }
    public void updateProfile(String name, String major, String interestTags, String availableTime) {
        this.name = name; this.major = major; this.interestTags = interestTags; this.availableTime = availableTime;
    }
}
