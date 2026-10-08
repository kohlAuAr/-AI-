package com.campus.business.activity;

import java.util.List;
import java.security.Principal;
import java.time.LocalDateTime;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ActivityController {
    private final ActivityService activities;
    public ActivityController(ActivityService activities) { this.activities = activities; }
    @GetMapping("/activities")
    public List<ActivityService.ActivityView> list() { return activities.published(); }
    @GetMapping("/activities/{id}")
    public ActivityService.ActivityView detail(@PathVariable Long id) { return activities.detail(id); }
    @GetMapping("/manage/clubs/{clubId}/activities")
    public List<ActivityService.ActivityView> managed(Principal user, @PathVariable Long clubId) { return activities.managed(user, clubId); }
    @PostMapping("/manage/clubs/{clubId}/activities")
    public ActivityService.ActivityView draft(Principal user, @PathVariable Long clubId, @Valid @RequestBody DraftRequest body) {
        return activities.draft(user, clubId, body);
    }
    @PostMapping("/manage/activities/{id}/publish")
    public ActivityService.ActivityView publish(Principal user, @PathVariable Long id) { return activities.publish(user, id); }
    @PutMapping("/manage/activities/{id}")
    public ActivityService.ActivityView update(Principal user, @PathVariable Long id, @Valid @RequestBody DraftRequest body) { return activities.update(user, id, body); }
    @PostMapping("/manage/activities/{id}/cancel")
    public ActivityService.ActivityView cancelActivity(Principal user, @PathVariable Long id, @Valid @RequestBody CancelRequest body) { return activities.cancelActivity(user, id, body.reason()); }
    @GetMapping("/manage/activities/{id}/check-in")
    public ActivityService.CheckInView checkInState(Principal user, @PathVariable Long id) { return activities.checkInState(user, id); }
    @PostMapping("/manage/activities/{id}/check-in/open")
    public ActivityService.CheckInView openCheckIn(Principal user, @PathVariable Long id) { return activities.openCheckIn(user, id); }
    @PostMapping("/manage/activities/{id}/check-in/close")
    public ActivityService.CheckInView closeCheckIn(Principal user, @PathVariable Long id) { return activities.closeCheckIn(user, id); }
    @PostMapping("/activities/{id}/check-in")
    public ActivityService.RegistrationView checkIn(Principal user, @PathVariable Long id, @Valid @RequestBody CheckInRequest body) { return activities.checkIn(user, id, body.code()); }
    @PostMapping("/activities/{id}/registrations")
    public ActivityService.RegistrationView register(Principal user, @PathVariable Long id) { return activities.register(user, id); }
    @PostMapping("/activities/{id}/registrations/cancel")
    public ActivityService.RegistrationView cancel(Principal user, @PathVariable Long id) { return activities.cancel(user, id); }
    @GetMapping("/registrations/mine")
    public List<ActivityService.RegistrationView> mine(Principal user) { return activities.mine(user); }
    @GetMapping("/manage/activities/{id}/registrations")
    public List<ActivityService.ParticipantView> participants(Principal user, @PathVariable Long id) { return activities.participants(user, id); }
    public record DraftRequest(@NotBlank @Size(max = 100) String title, @NotBlank @Size(max = 2000) String description,
                               @NotBlank @Size(max = 200) String location, @NotNull LocalDateTime startTime,
                               @NotNull LocalDateTime registrationDeadline, @Min(1) @Max(500) int capacity) {}
    public record CancelRequest(@NotBlank @Size(max = 300) String reason) {}
    public record CheckInRequest(@NotBlank @Size(max = 64) String code) {}
}
