package com.campus.business.ai;

import com.campus.business.identity.IdentityService;
import com.campus.business.membership.MembershipRepository;
import java.security.Principal;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AiIdentity {
    private final IdentityService identity;
    private final MembershipRepository memberships;
    public AiIdentity(IdentityService identity, MembershipRepository memberships) { this.identity = identity; this.memberships = memberships; }
    public Actor current(Principal principal) {
        var account = identity.current(principal); var rows = memberships.findByUserIdOrderByIdDesc(account.getId());
        return new Actor(account.getId(), account.getRole(), rows.stream().map(row -> row.getClubId()).distinct().toList(),
                rows.stream().filter(row -> row.getRole().equals("MANAGER")).map(row -> row.getClubId()).distinct().toList());
    }
    public record Actor(Long id, String role, List<Long> clubs, List<Long> managedClubs) {}
}
