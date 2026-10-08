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
}
