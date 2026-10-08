package com.campus.business.registration;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegistrationRepository extends JpaRepository<Registration, Long> {
    Optional<Registration> findByActivityIdAndUserId(Long activityId, Long userId);
    long countByActivityIdAndStatus(Long activityId, String status);
    List<Registration> findByUserIdOrderByIdDesc(Long userId);
    List<Registration> findByActivityIdAndStatusOrderByIdAsc(Long activityId, String status);
    long countByActivityIdAndStatusAndCheckedInAtIsNotNull(Long activityId, String status);
}
