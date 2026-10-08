package com.campus.business.identity;

import com.campus.business.membership.Membership;
import com.campus.business.membership.MembershipRepository;
import java.security.Principal;
import java.util.List;
import org.springframework.core.env.*;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final IdentityService identity;
    private final MembershipRepository memberships;
    private final Environment environment;
    public AuthController(IdentityService identity, MembershipRepository memberships, Environment environment) {
        this.identity = identity; this.memberships = memberships; this.environment = environment;
    }
    @GetMapping("/session")
    public SessionView session(Principal principal, CsrfToken csrf) {
        UserView user = null;
        if (principal != null) {
            Account account = identity.current(principal);
            List<Long> managed = memberships.findByUserIdAndRole(account.getId(), "MANAGER").stream().map(Membership::getClubId).toList();
            user = new UserView(account.getId(), account.getUsername(), account.getName(), account.getMajor(), account.getRole(), managed);
        }
        return new SessionView(user, csrf.getToken(), csrf.getHeaderName(), environment.acceptsProfiles(Profiles.of("demo")));
    }
    public record UserView(Long id, String username, String name, String major, String role, List<Long> managedClubIds) {}
    public record SessionView(UserView user, String csrfToken, String csrfHeader, boolean demoAccounts) {}
}
