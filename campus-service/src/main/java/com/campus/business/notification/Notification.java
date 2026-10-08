package com.campus.business.notification;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "station_notification", indexes = @Index(columnList = "userId,id"))
public class Notification {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long userId;
    private String type;
    private Long sourceId;
    private String title;
    @Column(length = 600)
    private String content;
    private String targetPath;
    private Instant createdAt;
    private Instant readAt;
    protected Notification() {}
    public Notification(Long userId, String type, Long sourceId, String title, String content, String targetPath) {
        this.userId = userId; this.type = type; this.sourceId = sourceId; this.title = title;
        this.content = content; this.targetPath = targetPath; this.createdAt = Instant.now();
    }
    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getType() { return type; }
    public Long getSourceId() { return sourceId; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public String getTargetPath() { return targetPath; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getReadAt() { return readAt; }
}
