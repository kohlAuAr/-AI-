package com.campus.business.identity;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class ProfileService {
    private final AccountRepository accounts;
    private final IdentityService identity;
    private final PasswordEncoder passwords;
    public ProfileService(AccountRepository accounts, IdentityService identity, PasswordEncoder passwords) {
        this.accounts = accounts; this.identity = identity; this.passwords = passwords;
    }
    @Transactional
    public Profile register(ProfileController.RegisterRequest body) {
        if (body.password().getBytes(StandardCharsets.UTF_8).length > 72)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "密码 UTF-8 编码不能超过 72 字节");
        if (accounts.findByUsername(body.username()).isPresent())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "账号已存在");
        return view(accounts.saveAndFlush(new Account(body.username(), passwords.encode(body.password()), body.name().trim(), body.major().trim(), "STUDENT")));
    }
    public Profile mine(Principal principal) { return view(identity.current(principal)); }
    @Transactional
    public Profile update(Principal principal, ProfileController.ProfileRequest body) {
        List<String> tags = body.interests().stream().map(String::trim).distinct().toList();
        if (tags.stream().anyMatch(tag -> tag.contains(","))) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "单个兴趣标签不能含逗号");
        Account user = identity.current(principal);
        user.updateProfile(body.name().trim(), body.major().trim(), String.join(",", tags), body.availableTime().trim());
        return view(user);
    }
    private Profile view(Account account) {
        List<String> tags = account.getInterestTags().isEmpty() ? List.of() : List.of(account.getInterestTags().split(","));
        return new Profile(account.getId(), account.getUsername(), account.getName(), account.getMajor(), account.getRole(), tags, account.getAvailableTime());
    }
    public record Profile(Long id, String username, String name, String major, String role, List<String> interests, String availableTime) {}
}
