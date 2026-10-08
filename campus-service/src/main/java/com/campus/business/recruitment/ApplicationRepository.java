package com.campus.business.recruitment;

import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface ApplicationRepository extends JpaRepository<ClubApplication, Long> {
    boolean existsByActiveKey(String activeKey);
    List<ClubApplication> findByUserIdOrderByIdDesc(Long userId);
    List<ClubApplication> findByClubIdOrderByIdDesc(Long clubId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from ClubApplication a where a.id = :id")
    Optional<ClubApplication> findLockedById(Long id);
}
