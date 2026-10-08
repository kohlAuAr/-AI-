package com.campus.business.notification;

import com.campus.business.identity.IdentityService;
import java.security.Principal;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class NotificationService {
    private final NotificationRepository notifications;
    private final IdentityService identity;
    public NotificationService(NotificationRepository notifications, IdentityService identity) {
        this.notifications = notifications; this.identity = identity;
    }
    // Called only by accepted business transitions; shares their transaction and row lock.
    @Transactional(propagation = Propagation.MANDATORY)
    public void send(Long userId, String type, Long sourceId, String title, String content, String targetPath) {
        notifications.saveAndFlush(new Notification(userId, type, sourceId, title, content, targetPath));
    }
    public Inbox inbox(Principal principal, int page) {
        if (page < 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "页码不能小于零");
        Long userId = identity.current(principal).getId();
        var rows = notifications.findByUserIdOrderByIdDesc(userId, PageRequest.of(page, 20));
        return new Inbox(rows.map(this::view).getContent(), rows.getTotalElements(), page, rows.hasNext(), notifications.countByUserIdAndReadAtIsNull(userId));
    }
    @Transactional
    public NotificationView read(Principal principal, Long id) {
        Long userId = identity.current(principal).getId();
        if (notifications.findByIdAndUserId(id, userId).isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "消息不存在");
        notifications.markRead(userId, id, Instant.now());
        return view(notifications.findByIdAndUserId(id, userId).orElseThrow());
    }
    @Transactional
    public ReadResult readAll(Principal principal) {
        return new ReadResult(notifications.markAllRead(identity.current(principal).getId(), Instant.now()));
    }
    private NotificationView view(Notification n) {
        return new NotificationView(n.getId(), n.getType(), n.getSourceId(), n.getTitle(), n.getContent(), n.getTargetPath(), n.getCreatedAt(), n.getReadAt());
    }
    public record NotificationView(Long id, String type, Long sourceId, String title, String content, String targetPath, Instant createdAt, Instant readAt) {}
    public record Inbox(List<NotificationView> items, long total, int page, boolean hasMore, long unread) {}
    public record ReadResult(int changed) {}
}
