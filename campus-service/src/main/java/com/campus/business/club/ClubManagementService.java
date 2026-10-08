package com.campus.business.club;

import com.campus.business.identity.*;
import com.campus.business.membership.*;
import java.security.Principal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class ClubManagementService {
    private final ClubRepository clubs;
    private final MembershipRepository memberships;
    private final IdentityService identity;
    public ClubManagementService(ClubRepository clubs, MembershipRepository memberships, IdentityService identity) {
        this.clubs = clubs; this.memberships = memberships; this.identity = identity;
    }
    public List<ClubController.ClubView> mine(Principal principal) {
        return memberships.findByUserIdAndRole(identity.current(principal).getId(), "MANAGER").stream()
                .map(m -> view(clubs.findById(m.getClubId()).orElseThrow())).toList();
    }
    @Transactional
    public ClubController.ClubView create(Principal principal, ClubManagementController.ClubRequest body) {
        Account user = identity.current(principal);
        if (!user.getRole().equals("MANAGER")) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅负责人可创建社团");
        Club club = clubs.saveAndFlush(Club.create(body));
        memberships.saveAndFlush(new Membership(user.getId(), club.getId(), "MANAGER"));
        return view(club);
    }
    @Transactional
    public ClubController.ClubView update(Principal principal, Long id, ClubManagementController.ClubRequest body) {
        Club club = clubs.findLockedById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "社团不存在"));
        if (!memberships.existsByUserIdAndClubIdAndRole(identity.current(principal).getId(), id, "MANAGER"))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能维护自己负责的社团");
        club.update(body); clubs.flush(); return view(club);
    }
    private ClubController.ClubView view(Club club) {
        return new ClubController.ClubView(club.getId(), club.getSlug(), club.getName(), club.getCategory(), club.getDescription(), club.getTags(), club.getCampus(),
                club.isDemo(), club.isRecruiting(), club.getRequirements(), club.getSchedule(), club.getPlace(), memberships.countByClubId(club.getId()));
    }
}
