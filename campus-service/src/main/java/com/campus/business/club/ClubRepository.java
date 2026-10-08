package com.campus.business.club;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClubRepository extends JpaRepository<Club, Long> {
    List<Club> findAllByOrderByIdAsc();
    Optional<Club> findBySlug(String slug);
    Optional<Club> findByName(String name);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Club c where c.id = :id")
    Optional<Club> findLockedById(Long id);
}
