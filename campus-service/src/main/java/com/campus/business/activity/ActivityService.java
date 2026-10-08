package com.campus.business.activity;

import com.campus.business.identity.*;
import com.campus.business.membership.MembershipRepository;
import com.campus.business.registration.*;
import com.campus.business.notification.NotificationService;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
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
        if (!activity.getStatus().equals("PUBLISHED") && !(activity.getStatus().equals("CANCELLED") && activity.getPublishedAt() != null))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "活动尚未发布或不存在");
        return view(activity);
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
    public ActivityView update(Principal principal, Long id, ActivityController.DraftRequest body) {
        Activity activity = locked(id); Account manager = requireManager(principal, activity.getClubId());
        if (!List.of("DRAFT", "PUBLISHED").contains(activity.getStatus())) throw conflict("该状态的活动不能编辑");
        if (!LocalDateTime.now().isBefore(activity.getStartTime())) throw conflict("活动已开始，不能编辑");
        if (!activity.getStatus().equals("PUBLISHED") || !body.registrationDeadline().equals(activity.getRegistrationDeadline()))
            validateTimes(body.startTime(), body.registrationDeadline());
        if (!LocalDateTime.now().isBefore(body.startTime()) || body.registrationDeadline().isAfter(body.startTime()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "开始时间须在未来，截止不能晚于开始");
        long enrolled = registrations.countByActivityIdAndStatus(id, "REGISTERED");
        if (body.capacity() < enrolled) throw conflict("容量不能少于已报名人数");
        if (enrolled > 0 && (!activity.getStartTime().equals(body.startTime()) || !activity.getLocation().equals(body.location().trim())))
            throw conflict("已有学生报名，不能调整活动时间或地点；需要时取消后重新发布新活动");
        activity.update(body, manager.getId()); return view(activity);
    }
    @Transactional
    public ActivityView cancelActivity(Principal principal, Long id, String reason) {
        Activity activity = locked(id); Account manager = requireManager(principal, activity.getClubId());
        if (!List.of("DRAFT", "PUBLISHED").contains(activity.getStatus())) throw conflict("活动已取消或不可取消");
        if (!LocalDateTime.now().isBefore(activity.getStartTime())) throw conflict("活动已开始，不能取消");
        if (registrations.countByActivityIdAndStatusAndCheckedInAtIsNotNull(id, "REGISTERED") > 0) throw conflict("已有学生签到，不能取消活动");
        activity.cancel(reason, manager.getId());
        for (Registration registration : registrations.findByActivityIdAndStatusOrderByIdAsc(id, "REGISTERED")) {
            registration.cancel();
            notifications.send(registration.getUserId(), "ACTIVITY_WITHDRAWN", id, "活动已取消", "「" + activity.getTitle() + "」已由负责人取消。原因：" + reason.trim(), "/activities/" + id);
        }
        return view(activity);
    }
    public CheckInView checkInState(Principal principal, Long id) {
        Activity activity = find(id); requireManager(principal, activity.getClubId()); return checkInView(activity);
    }
    @Transactional
    public CheckInView openCheckIn(Principal principal, Long id) {
        Activity activity = locked(id); requireManager(principal, activity.getClubId()); requirePublished(activity);
        if (LocalDateTime.now().isBefore(activity.getStartTime().minusMinutes(30))) throw conflict("活动开始前 30 分钟才能开启签到");
        if (!activity.isCheckInOpen()) activity.openCheckIn(UUID.randomUUID().toString());
        return checkInView(activity);
    }
    @Transactional
    public CheckInView closeCheckIn(Principal principal, Long id) {
        Activity activity = locked(id); requireManager(principal, activity.getClubId());
        activity.closeCheckIn(); return checkInView(activity);
    }
    @Transactional
    public RegistrationView checkIn(Principal principal, Long id, String code) {
        Account student = requireStudent(principal); Activity activity = locked(id); requirePublished(activity);
        if (!activity.isCheckInOpen()) throw conflict("签到未开放或已关闭");
        if (!code.equals(activity.getCheckInCode())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "签到码不正确");
        Registration registration = registrations.findByActivityIdAndUserId(id, student.getId())
                .filter(r -> r.getStatus().equals("REGISTERED")).orElseThrow(() -> conflict("请先报名，取消记录不能签到"));
        if (registration.getCheckedInAt() != null) return registrationView(registration);
        registration.checkIn(); return registrationView(registration);
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
        if (registration.getCheckedInAt() != null) throw conflict("已签到，不能取消报名");
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
            return new ParticipantView(r.getId(), student.getName(), student.getMajor(), r.getRegisteredAt(), r.getCheckedInAt());
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
                a.getStatus(), a.isDemo(), a.getCreatedBy(), a.getPublishedAt(), a.getUpdatedAt(), a.getUpdatedBy(),
                a.getCancelledAt(), a.getCancelledBy(), a.getCancelReason(), a.isCheckInOpen());
    }
    private RegistrationView registrationView(Registration r) {
        Activity a = find(r.getActivityId());
        return new RegistrationView(r.getId(), a.getId(), a.getClubId(), a.getTitle(), a.getStartTime(), a.getLocation(), r.getStatus(), r.getRegisteredAt(), r.getCancelledAt(), r.getCheckedInAt());
    }
    private CheckInView checkInView(Activity a) {
        return new CheckInView(a.getId(), a.isCheckInOpen(), a.getCheckInCode(), registrations.countByActivityIdAndStatus(a.getId(), "REGISTERED"),
                registrations.countByActivityIdAndStatusAndCheckedInAtIsNotNull(a.getId(), "REGISTERED"));
    }
    public record ActivityView(Long id, Long clubId, String title, String description, String location, LocalDateTime startTime,
                               LocalDateTime registrationDeadline, int capacity, long enrolled, String status, boolean demo,
                               Long createdBy, LocalDateTime publishedAt, LocalDateTime updatedAt, Long updatedBy,
                               LocalDateTime cancelledAt, Long cancelledBy, String cancelReason, boolean checkInOpen) {}
    public record RegistrationView(Long id, Long activityId, Long clubId, String title, LocalDateTime startTime, String location,
                                   String status, LocalDateTime registeredAt, LocalDateTime cancelledAt, LocalDateTime checkedInAt) {}
    public record ParticipantView(Long id, String name, String major, LocalDateTime registeredAt, LocalDateTime checkedInAt) {}
    public record CheckInView(Long activityId, boolean open, String code, long registered, long checkedIn) {}
}
