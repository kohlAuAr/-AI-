package com.campus.business.favorite;

import java.security.Principal;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/favorites")
public class FavoriteController {
    private final FavoriteService favorites;
    public FavoriteController(FavoriteService favorites) { this.favorites = favorites; }
    @GetMapping
    public List<FavoriteService.FavoriteView> mine(Principal user) { return favorites.mine(user); }
    @PutMapping("/{clubId}")
    public FavoriteService.FavoriteView add(Principal user, @PathVariable Long clubId) { return favorites.add(user, clubId); }
    @DeleteMapping("/{clubId}")
    public FavoriteService.Removal remove(Principal user, @PathVariable Long clubId) { return favorites.remove(user, clubId); }
}
