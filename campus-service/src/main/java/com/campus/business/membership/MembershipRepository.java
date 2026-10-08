package com.campus.business.membership;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MembershipRepository extends JpaRepository<Membership, Long> {
    boolean existsByUserIdAndClubId(Long userId, Long clubId);
    boolean existsByUserIdAndClubIdAndRole(Long userId, Long clubId, String role);
    List<Membership> findByUserIdAndRole(Long userId, String role);
    List<Membership> findByUserIdOrderByIdDesc(Long userId);
    List<Membership> findByClubIdOrderByIdDesc(Long clubId);
    long countByClubId(Long clubId);
}
