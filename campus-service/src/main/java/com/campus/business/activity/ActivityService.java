package com.campus.business.activity;

import com.campus.business.identity.*;
import com.campus.business.membership.MembershipRepository;
import com.campus.business.registration.*;
import com.campus.business.notification.NotificationService;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class ActivityService {
    private final ActivityRepository activities;
    private final RegistrationRepository registrations;
    private final IdentityService identity;
    private final MembershipRepository memberships;
    private final AccountRepository accounts;
    private final NotificationService notifications;
    public ActivityService(ActivityRepository activities, RegistrationRepository registrations, IdentityService identity,
                           MembershipRepository memberships, AccountRepository accounts, NotificationService notifications) {
        this.activities = activities; this.registrations = registrations; this.identity = identity;
        this.memberships = memberships; this.accounts = accounts;
        this.notifications = notifications;
    }
    public List<ActivityView> published() { return activities.findByStatusOrderByStartTimeAsc("PUBLISHED").stream().map(this::view).toList(); }
    public ActivityView detail(Long id) {
        Activity activity = find(id);
        requirePublished(activity); return view(activity);
    }
    public List<ActivityView> managed(Principal principal, Long clubId) {
        requireManager(principal, clubId);
        return activities.findByClubIdOrderByIdDesc(clubId).stream().filter(a -> !a.getStatus().equals("SAMPLE")).map(this::view).toList();
    }
    @Transactional
    public ActivityView draft(Principal principal, Long clubId, ActivityController.DraftRequest body) {
        Account manager = requireManager(principal, clubId);
        validateTimes(body.startTime(), body.registrationDeadline());
        return view(activities.saveAndFlush(Activity.draft(clubId, body.title(), body.description(), body.location(),
                body.startTime(), body.registrationDeadline(), body.capacity(), manager.getId())));
    }
    @Transactional
    public ActivityView publish(Principal principal, Long id) {
        Activity activity = locked(id); requireManager(principal, activity.getClubId());
        if (!activity.getStatus().equals("DRAFT")) throw conflict("只有草稿可以发布，请刷新列表");
        validateTimes(activity.getStartTime(), activity.getRegistrationDeadline());
        activity.publish(); return view(activity);
    }
    @Transactional
    public RegistrationView register(Principal principal, Long id) {
        Account student = requireStudent(principal);
        // Serialize signup/cancellation against the same activity row; the final seat cannot be oversold.
        Activity activity = locked(id); requirePublished(activity);
        if (!LocalDateTime.now().isBefore(activity.getRegistrationDeadline())) throw conflict("报名已截止");
        Registration registration = registrations.findByActivityIdAndUserId(id, student.getId()).orElse(null);
        if (registration != null && registration.getStatus().equals("REGISTERED")) throw conflict("你已报名，请勿重复提交");
        if (registrations.countByActivityIdAndStatus(id, "REGISTERED") >= activity.getCapacity()) throw conflict("活动名额已满");
        if (registration == null) registration = new Registration(id, student.getId()); else registration.activate();
        registration = registrations.saveAndFlush(registration);
        notifications.send(student.getId(), "ACTIVITY_REGISTERED", activity.getId(), "活动报名成功", "你已报名「" + activity.getTitle() + "」，可查看活动时间与地点。", "/activities/" + activity.getId());
        return registrationView(registration);
    }
    @Transactional
    public RegistrationView cancel(Principal principal, Long id) {
        Account student = requireStudent(principal);
        Activity activity = locked(id); requirePublished(activity);
        if (!LocalDateTime.now().isBefore(activity.getStartTime())) throw conflict("活动已开始，不能取消报名");
        Registration registration = registrations.findByActivityIdAndUserId(id, student.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "你没有该活动的报名记录"));
        if (!registration.getStatus().equals("REGISTERED")) throw conflict("报名已取消，请刷新列表");
        registration.cancel();
        notifications.send(student.getId(), "ACTIVITY_CANCELLED", activity.getId(), "活动报名已取消", "你已取消「" + activity.getTitle() + "」的报名，名额已释放。", "/activities/" + activity.getId());
        return registrationView(registration);
    }
    public List<RegistrationView> mine(Principal principal) {
        return registrations.findByUserIdOrderByIdDesc(identity.current(principal).getId()).stream().map(this::registrationView).toList();
    }
    public List<ParticipantView> participants(Principal principal, Long id) {
        Activity activity = find(id); requireManager(principal, activity.getClubId());
        return registrations.findByActivityIdAndStatusOrderByIdAsc(id, "REGISTERED").stream().map(r -> {
            Account student = accounts.findById(r.getUserId()).orElseThrow();
            return new ParticipantView(r.getId(), student.getName(), student.getMajor(), r.getRegisteredAt());
        }).toList();
    }
    private Account requireManager(Principal principal, Long clubId) {
        Account user = identity.current(principal);
        if (!memberships.existsByUserIdAndClubIdAndRole(user.getId(), clubId, "MANAGER"))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能管理自己负责社团的活动");
        return user;
    }
    private Account requireStudent(Principal principal) {
        Account user = identity.current(principal);
        if (!user.getRole().equals("STUDENT")) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "请使用学生账号报名");
        return user;
    }
    private Activity find(Long id) { return activities.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "活动不存在")); }
    private Activity locked(Long id) { return activities.findLockedById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "活动不存在")); }
    private void requirePublished(Activity activity) {
        if (!activity.getStatus().equals("PUBLISHED")) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "活动尚未发布或不存在");
    }
    private void validateTimes(LocalDateTime start, LocalDateTime deadline) {
        if (!LocalDateTime.now().isBefore(deadline) || deadline.isAfter(start))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "报名截止时间须晚于当前时间，且不晚于活动开始时间");
    }
    private ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
    private ActivityView view(Activity a) {
        return new ActivityView(a.getId(), a.getClubId(), a.getTitle(), a.getDescription(), a.getLocation(), a.getStartTime(),
                a.getRegistrationDeadline(), a.getCapacity(), registrations.countByActivityIdAndStatus(a.getId(), "REGISTERED"),
                a.getStatus(), a.isDemo(), a.getCreatedBy(), a.getPublishedAt());
    }
    private RegistrationView registrationView(Registration r) {
        Activity a = find(r.getActivityId());
        return new RegistrationView(r.getId(), a.getId(), a.getClubId(), a.getTitle(), a.getStartTime(), a.getLocation(), r.getStatus(), r.getRegisteredAt(), r.getCancelledAt());
    }
    public record ActivityView(Long id, Long clubId, String title, String description, String location, LocalDateTime startTime,
                               LocalDateTime registrationDeadline, int capacity, long enrolled, String status, boolean demo,
                               Long createdBy, LocalDateTime publishedAt) {}
    public record RegistrationView(Long id, Long activityId, Long clubId, String title, LocalDateTime startTime, String location,
                                   String status, LocalDateTime registeredAt, LocalDateTime cancelledAt) {}
    public record ParticipantView(Long id, String name, String major, LocalDateTime registeredAt) {}
}
