package com.campus.business.recruitment;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.security.Principal;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class RecruitmentController {
    private final RecruitmentService recruitment;
    public RecruitmentController(RecruitmentService recruitment) { this.recruitment = recruitment; }
    @PostMapping("/recruitment/applications")
    public RecruitmentService.ApplicationView apply(Principal user, @Valid @RequestBody ApplyRequest body) {
        return recruitment.apply(user, body.clubId(), body.reason());
    }
    @GetMapping("/recruitment/my-applications")
    public List<RecruitmentService.ApplicationView> mine(Principal user) { return recruitment.mine(user); }
    @PostMapping("/recruitment/applications/{id}/withdraw")
    public RecruitmentService.ApplicationView withdraw(Principal user, @PathVariable Long id) { return recruitment.withdraw(user, id); }
    @GetMapping("/memberships/mine")
    public List<RecruitmentService.MemberView> memberships(Principal user) { return recruitment.myMemberships(user); }
    @GetMapping("/manage/clubs/{clubId}/applications")
    public List<RecruitmentService.ApplicationView> managedApplications(Principal user, @PathVariable Long clubId) { return recruitment.managedApplications(user, clubId); }
    @GetMapping("/manage/clubs/{clubId}/members")
    public List<RecruitmentService.MemberView> members(Principal user, @PathVariable Long clubId) { return recruitment.managedMembers(user, clubId); }
    @PostMapping("/manage/applications/{id}/review")
    public RecruitmentService.ApplicationView review(Principal user, @PathVariable Long id, @Valid @RequestBody ReviewRequest body) {
        return recruitment.review(user, id, body.approved(), body.feedback());
    }
    public record ApplyRequest(@NotNull @Positive Long clubId, @NotBlank @Size(max = 500) String reason) {}
    public record ReviewRequest(@NotNull Boolean approved, @Size(max = 300) String feedback) {}
}
