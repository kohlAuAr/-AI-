package com.campus.business.finance;

import java.math.BigDecimal;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {
    List<Expense> findByActivityIdOrderByIdDesc(Long activityId);
    Optional<Expense> findByActivityIdAndRequestId(Long activityId, String requestId);
    @Query("select e.activityId from Expense e where e.id = :id")
    Optional<Long> findActivityIdById(Long id);
    @Query("select coalesce(sum(e.amount), 0) from Expense e where e.activityId = :activityId and e.status = 'ACTIVE'")
    BigDecimal totalSpent(Long activityId);
}
