package com.campus.business.club;

import jakarta.persistence.*;

@Entity
@Table(name = "club")
public class Club {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true)
    private String name;
    private String category;
    @Column(length = 2000)
    private String description;
    private String tags;
    private String campus;
    private boolean demo;
    @Column(unique = true, length = 64)
    private String slug;
    @Column(columnDefinition = "boolean default true")
    private boolean recruiting = true;
    @Column(length = 500)
    private String requirements;
    private String schedule;
    private String place;

    protected Club() {}

    public Club(String name, String category, String description, String tags, String campus) {
        this.name = name;
        this.category = category;
        this.description = description;
        this.tags = tags;
        this.campus = campus;
        this.demo = true;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public String getDescription() { return description; }
    public String getTags() { return tags; }
    public String getCampus() { return campus; }
    public boolean isDemo() { return demo; }
    public String getSlug() { return slug; }
    public boolean isRecruiting() { return recruiting; }
    public String getRequirements() { return requirements; }
    public String getSchedule() { return schedule; }
    public String getPlace() { return place; }
    public void configureRecruitment(String slug, boolean recruiting, String requirements, String schedule, String place) {
        this.slug = slug; this.recruiting = recruiting; this.requirements = requirements; this.schedule = schedule; this.place = place;
    }
}
