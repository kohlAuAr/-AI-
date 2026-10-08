package com.campus.business.banner;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "homepage_banner")
public class Banner {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 80)
    private String title;
    private String targetType;
    private Long targetId;
    private int sortOrder;
    private boolean enabled;
    private LocalDateTime startsAt;
    private LocalDateTime endsAt;
    @Lob @Column(nullable = false, columnDefinition = "LONGBLOB")
    private byte[] image;
    private String contentType;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long updatedBy;

    protected Banner() {}
    public Banner(BannerController.BannerRequest body, byte[] image, String contentType, Long actor) {
        this.image = image; this.contentType = contentType; createdAt = LocalDateTime.now();
        update(body, actor);
    }
    public void update(BannerController.BannerRequest body, Long actor) {
        title = body.title().trim(); targetType = body.targetType().name(); targetId = body.targetId();
        sortOrder = body.sortOrder(); startsAt = body.startsAt(); endsAt = body.endsAt(); touch(actor);
    }
    public void replaceImage(byte[] bytes, String type) { image = bytes; contentType = type; }
    public void setEnabled(boolean value, Long actor) { enabled = value; touch(actor); }
    private void touch(Long actor) { updatedAt = LocalDateTime.now(); updatedBy = actor; }
    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getTargetType() { return targetType; }
    public Long getTargetId() { return targetId; }
    public int getSortOrder() { return sortOrder; }
    public boolean isEnabled() { return enabled; }
    public LocalDateTime getStartsAt() { return startsAt; }
    public LocalDateTime getEndsAt() { return endsAt; }
    public byte[] getImage() { return image; }
    public String getContentType() { return contentType; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public Long getUpdatedBy() { return updatedBy; }
}
