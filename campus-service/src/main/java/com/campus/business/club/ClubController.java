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
    private final ClubCatalogCache cache;

    public ClubController(ClubRepository clubs, MembershipRepository memberships, ClubCatalogCache cache) {
        this.clubs = clubs;
        this.memberships = memberships;
        this.cache = cache;
    }

    @GetMapping
    public List<ClubView> list() {
        if (!cache.enabled()) return clubs.findAllByOrderByIdAsc().stream().map(c -> withMembers(view(c))).toList();
        return clubs.findIds().stream().map(this::detail).toList();
    }

    @GetMapping("/{id}")
    public ClubView detail(@PathVariable Long id) {
        return withMembers(cache.get(id, () -> view(clubs.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "社团不存在")))));
    }
    private ClubView view(Club club) {
        return new ClubView(club.getId(), club.getSlug(), club.getName(), club.getCategory(), club.getDescription(), club.getTags(), club.getCampus(),
                club.isDemo(), club.isRecruiting(), club.getRequirements(), club.getSchedule(), club.getPlace(), 0);
    }
    private ClubView withMembers(ClubView club) {
        return new ClubView(club.id(), club.slug(), club.name(), club.category(), club.description(), club.tags(), club.campus(),
                club.demo(), club.recruiting(), club.requirements(), club.schedule(), club.place(), memberships.countByClubId(club.id()));
    }
    public record ClubView(Long id, String slug, String name, String category, String description, String tags, String campus,
                           boolean demo, boolean recruiting, String requirements, String schedule, String place, long members) {}
}
