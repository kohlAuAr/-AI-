package com.campus.business.identity;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.security.Principal;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ProfileController {
    private final ProfileService profiles;
    public ProfileController(ProfileService profiles) { this.profiles = profiles; }
    @PostMapping("/auth/register")
    public ProfileService.Profile register(@Valid @RequestBody RegisterRequest body) { return profiles.register(body); }
    @GetMapping("/profile")
    public ProfileService.Profile mine(Principal user) { return profiles.mine(user); }
    @PutMapping("/profile")
    public ProfileService.Profile update(Principal user, @Valid @RequestBody ProfileRequest body) { return profiles.update(user, body); }
    public record RegisterRequest(@NotBlank @Pattern(regexp = "[a-z0-9_]{3,32}") String username,
                                  @NotBlank @Size(min = 8, max = 72) String password,
                                  @NotBlank @Size(max = 40) String name, @NotBlank @Size(max = 80) String major) {}
    public record ProfileRequest(@NotBlank @Size(max = 40) String name, @NotBlank @Size(max = 80) String major,
                                 @NotNull @Size(max = 12) List<@NotBlank @Size(max = 20) String> interests,
                                 @NotNull @Size(max = 200) String availableTime) {}
}
