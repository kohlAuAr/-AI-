package com.campus.business.recruitment;

import com.campus.business.club.*;
import com.campus.business.identity.*;
import com.campus.business.membership.*;
import com.campus.business.notification.NotificationService;
import java.security.Principal;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class RecruitmentService {
    private final IdentityService identity;
    private final AccountRepository accounts;
    private final ClubRepository clubs;
    private final ApplicationRepository applications;
    private final MembershipRepository memberships;
    private final NotificationService notifications;
    public RecruitmentService(IdentityService identity, AccountRepository accounts, ClubRepository clubs,
                              ApplicationRepository applications, MembershipRepository memberships, NotificationService notifications) {
        this.identity = identity; this.accounts = accounts; this.clubs = clubs;
        this.applications = applications; this.memberships = memberships;
        this.notifications = notifications;
    }
    @Transactional
    public ApplicationView apply(Principal principal, Long clubId, String reason) {
        Account user = identity.current(principal);
        if (!user.getRole().equals("STUDENT")) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅学生账号可提交入社申请");
        Club club = clubs.findLockedById(clubId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "社团不存在"));
        if (!club.isRecruiting()) throw new ResponseStatusException(HttpStatus.CONFLICT, "本轮招新已结束");
        if (memberships.existsByUserIdAndClubId(user.getId(), clubId) || applications.existsByActiveKey(user.getId() + ":" + clubId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "已有待审核申请或已加入社团，请勿重复申请");
        }
        return view(applications.saveAndFlush(new ClubApplication(user.getId(), clubId, reason.trim())));
    }
    public List<ApplicationView> mine(Principal principal) {
        return applications.findByUserIdOrderByIdDesc(identity.current(principal).getId()).stream().map(this::view).toList();
    }
    public List<ApplicationView> managedApplications(Principal principal, Long clubId) {
        requireManager(principal, clubId);
        return applications.findByClubIdOrderByIdDesc(clubId).stream().map(this::view).toList();
    }
    @Transactional
    public ApplicationView review(Principal principal, Long id, boolean approved, String feedback) {
        ClubApplication application = lockedApplication(id);
        Account reviewer = requireManager(principal, application.getClubId());
        requirePending(application);
        if (approved) {
            if (memberships.existsByUserIdAndClubId(application.getUserId(), application.getClubId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "该学生已是社团成员");
            }
            memberships.saveAndFlush(new Membership(application.getUserId(), application.getClubId(), "MEMBER"));
        }
        application.review(approved, feedback == null ? "" : feedback.trim(), reviewer.getId());
        String name = clubs.findById(application.getClubId()).orElseThrow().getName();
        notifications.send(application.getUserId(), approved ? "APPLICATION_APPROVED" : "APPLICATION_REJECTED", application.getId(),
                approved ? "入社申请已通过" : "入社申请未通过", "「" + name + "」的申请" + (approved ? "已通过审核。" : "未通过审核。") + application.getFeedback(), "/me?tab=applications");
        return view(application);
    }
    @Transactional
    public ApplicationView withdraw(Principal principal, Long id) {
        ClubApplication application = lockedApplication(id);
        if (!application.getUserId().equals(identity.current(principal).getId())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能撤回自己的申请");
        requirePending(application); application.withdraw(); return view(application);
    }
    public List<MemberView> myMemberships(Principal principal) {
        return memberships.findByUserIdOrderByIdDesc(identity.current(principal).getId()).stream().map(this::memberView).toList();
    }
    public List<MemberView> managedMembers(Principal principal, Long clubId) {
        requireManager(principal, clubId);
        return memberships.findByClubIdOrderByIdDesc(clubId).stream().map(this::memberView).toList();
    }
    private Account requireManager(Principal principal, Long clubId) {
        Account user = identity.current(principal);
        if (!memberships.existsByUserIdAndClubIdAndRole(user.getId(), clubId, "MANAGER")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能管理自己负责的社团");
        }
        return user;
    }
    private ClubApplication lockedApplication(Long id) {
        return applications.findLockedById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "申请不存在"));
    }
    private void requirePending(ClubApplication application) {
        if (!application.getStatus().equals("pending")) throw new ResponseStatusException(HttpStatus.CONFLICT, "申请已处理，请刷新列表");
    }
    private ApplicationView view(ClubApplication application) {
        Account user = accounts.findById(application.getUserId()).orElseThrow();
        return new ApplicationView(application.getId(), application.getClubId(), user.getId(), user.getName(), user.getMajor(),
                application.getReason(), application.getStatus(), application.getFeedback(), application.getCreatedAt(), application.getReviewedBy(), application.getReviewedAt());
    }
    private MemberView memberView(Membership member) {
        Account user = accounts.findById(member.getUserId()).orElseThrow();
        return new MemberView(member.getId(), member.getClubId(), user.getId(), user.getName(), user.getMajor(), member.getRole(), member.getJoinedAt());
    }
    public record ApplicationView(Long id, Long clubId, Long userId, String name, String major, String reason, String status, String feedback, Instant createdAt, Long reviewedBy, Instant reviewedAt) {}
    public record MemberView(Long id, Long clubId, Long userId, String name, String major, String role, Instant joinedAt) {}
}
