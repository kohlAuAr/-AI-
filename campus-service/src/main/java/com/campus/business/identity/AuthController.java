package com.campus.business.identity;

import com.campus.business.membership.Membership;
import com.campus.business.membership.MembershipRepository;
import java.security.Principal;
import java.util.List;
import org.springframework.core.env.*;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.Map;
import org.springframework.security.core.context.SecurityContextHolder;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final IdentityService identity;
    private final MembershipRepository memberships;
    private final Environment environment;
    private final PasswordService passwords;
    public AuthController(IdentityService identity, MembershipRepository memberships, Environment environment, PasswordService passwords) {
        this.identity = identity; this.memberships = memberships; this.environment = environment; this.passwords = passwords;
    }
    @PostMapping("/password")
    public Map<String, String> changePassword(Principal principal, @Valid @RequestBody PasswordRequest body, HttpServletRequest request) {
        passwords.change(principal, body.currentPassword(), body.newPassword());
        request.getSession().invalidate();
        SecurityContextHolder.clearContext();
        return Map.of("status", "OK");
    }
    public record PasswordRequest(@NotBlank @Size(max = 72) String currentPassword, @NotBlank @Size(min = 8, max = 72) String newPassword) {}
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
