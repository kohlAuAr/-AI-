package com.campus.business.notification;

import java.security.Principal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationService notifications;
    public NotificationController(NotificationService notifications) { this.notifications = notifications; }
    @GetMapping
    public NotificationService.Inbox inbox(Principal user, @RequestParam(defaultValue = "0") int page) { return notifications.inbox(user, page); }
    @PostMapping("/{id}/read")
    public NotificationService.NotificationView read(Principal user, @PathVariable Long id) { return notifications.read(user, id); }
    @PostMapping("/read-all")
    public NotificationService.ReadResult readAll(Principal user) { return notifications.readAll(user); }
}
