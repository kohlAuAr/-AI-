package com.campus.business.club;

import java.util.List;
import com.campus.business.membership.MembershipRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/clubs")
public class ClubController {
    private final ClubRepository clubs;
    private final MembershipRepository memberships;

    public ClubController(ClubRepository clubs, MembershipRepository memberships) {
        this.clubs = clubs;
        this.memberships = memberships;
    }

    @GetMapping
    public List<ClubView> list() {
        return clubs.findAllByOrderByIdAsc().stream().map(this::view).toList();
    }

    @GetMapping("/{id}")
    public ClubView detail(@PathVariable Long id) {
        return view(clubs.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "社团不存在")));
    }
    private ClubView view(Club club) {
        return new ClubView(club.getId(), club.getSlug(), club.getName(), club.getCategory(), club.getDescription(), club.getTags(), club.getCampus(),
                club.isDemo(), club.isRecruiting(), club.getRequirements(), club.getSchedule(), club.getPlace(), memberships.countByClubId(club.getId()));
    }
    public record ClubView(Long id, String slug, String name, String category, String description, String tags, String campus,
                           boolean demo, boolean recruiting, String requirements, String schedule, String place, long members) {}
}
