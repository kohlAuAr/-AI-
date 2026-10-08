package com.campus.business.favorite;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FavoriteRepository extends JpaRepository<Favorite, Long> {
    List<Favorite> findByUserIdOrderByIdDesc(Long userId);
    Optional<Favorite> findByUserIdAndClubId(Long userId, Long clubId);
    long deleteByUserIdAndClubId(Long userId, Long clubId);
}
