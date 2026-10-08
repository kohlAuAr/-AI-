package com.campus.business.club;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/clubs")
public class ClubController {
    private final ClubRepository clubs;

    public ClubController(ClubRepository clubs) {
        this.clubs = clubs;
    }

    @GetMapping
    public List<Club> list() {
        return clubs.findAllByOrderByIdAsc();
    }

    @GetMapping("/{id}")
    public Club detail(@PathVariable Long id) {
        return clubs.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "社团不存在"));
    }
}
