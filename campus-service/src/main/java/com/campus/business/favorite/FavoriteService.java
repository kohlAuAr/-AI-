package com.campus.business.favorite;

import com.campus.business.club.*;
import com.campus.business.identity.IdentityService;
import java.security.Principal;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class FavoriteService {
    private final FavoriteRepository favorites;
    private final ClubRepository clubs;
    private final IdentityService identity;
    public FavoriteService(FavoriteRepository favorites, ClubRepository clubs, IdentityService identity) {
        this.favorites = favorites; this.clubs = clubs; this.identity = identity;
    }
    public List<FavoriteView> mine(Principal principal) {
        return favorites.findByUserIdOrderByIdDesc(identity.current(principal).getId()).stream().map(this::view).toList();
    }
    @Transactional
    public FavoriteView add(Principal principal, Long clubId) {
        Long userId = identity.current(principal).getId(); lockedClub(clubId);
        return view(favorites.findByUserIdAndClubId(userId, clubId).orElseGet(() -> favorites.saveAndFlush(new Favorite(userId, clubId))));
    }
    @Transactional
    public Removal remove(Principal principal, Long clubId) {
        Long userId = identity.current(principal).getId(); lockedClub(clubId);
        return new Removal(favorites.deleteByUserIdAndClubId(userId, clubId));
    }
    private void lockedClub(Long id) {
        clubs.findLockedById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "社团不存在"));
    }
    private FavoriteView view(Favorite favorite) {
        Club club = clubs.findById(favorite.getClubId()).orElseThrow();
        return new FavoriteView(favorite.getId(), club.getId(), club.getName(), favorite.getCreatedAt());
    }
    public record FavoriteView(Long id, Long clubId, String clubName, Instant createdAt) {}
    public record Removal(long changed) {}
}
