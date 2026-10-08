package com.campus.business.activity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.util.*;

public interface ActivityRepository extends JpaRepository<Activity, Long> {
    List<Activity> findByStatusOrderByStartTimeAsc(String status);
    List<Activity> findByClubIdOrderByIdDesc(Long clubId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Activity a where a.id = :id")
    Optional<Activity> findLockedById(Long id);
}
