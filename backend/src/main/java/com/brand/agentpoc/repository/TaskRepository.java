package com.brand.agentpoc.repository;

import com.brand.agentpoc.entity.Task;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskRepository extends JpaRepository<Task, Long> {
    List<Task> findByDealerCodeIgnoreCase(String dealerCode);

    List<Task> findByCityIgnoreCase(String city);

    List<Task> findByDealerGroupNameIgnoreCase(String dealerGroupName);

    List<Task> findByOpportunityIdIgnoreCase(String opportunityId);

    List<Task> findByStatusIgnoreCase(String status);

    List<Task> findByCreatedDateBetween(LocalDate startDate, LocalDate endDate);
}
