package com.campus.business.club;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.security.Principal;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/manage/clubs")
public class ClubManagementController {
    private final ClubManagementService clubs;
    public ClubManagementController(ClubManagementService clubs) { this.clubs = clubs; }
    @GetMapping
    public List<ClubController.ClubView> mine(Principal user) { return clubs.mine(user); }
    @PostMapping
    public ClubController.ClubView create(Principal user, @Valid @RequestBody ClubRequest body) { return clubs.create(user, body); }
    @PutMapping("/{id}")
    public ClubController.ClubView update(Principal user, @PathVariable Long id, @Valid @RequestBody ClubRequest body) { return clubs.update(user, id, body); }
    public record ClubRequest(@NotBlank @Size(max = 100) String name, @NotBlank @Size(max = 40) String category,
                              @NotBlank @Size(max = 2000) String description, @NotNull @Size(max = 255) String tags,
                              @NotBlank @Size(max = 100) String campus, @NotNull Boolean recruiting,
                              @NotBlank @Size(max = 500) String requirements, @NotBlank @Size(max = 200) String schedule,
                              @NotBlank @Size(max = 200) String place) {}
}
