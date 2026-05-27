package com.brand.agentpoc.repository;

import com.brand.agentpoc.entity.Opportunity;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OpportunityRepository extends JpaRepository<Opportunity, Long> {
    List<Opportunity> findByDealerCodeIgnoreCase(String dealerCode);

    List<Opportunity> findByDealerCodeIgnoreCaseAndCreatedDateBetween(
            String dealerCode,
            LocalDate startDate,
            LocalDate endDate
    );

    List<Opportunity> findByCityIgnoreCase(String city);

    List<Opportunity> findByDealerGroupNameIgnoreCase(String dealerGroupName);

    List<Opportunity> findByProductModelIgnoreCase(String productModel);

    List<Opportunity> findByStageNameIgnoreCase(String stageName);

    List<Opportunity> findByLeadSourceIgnoreCase(String leadSource);

    List<Opportunity> findByCreatedDateBetween(LocalDate startDate, LocalDate endDate);

    List<Opportunity> findByCreatedDateGreaterThanEqual(LocalDate startDate);

    List<Opportunity> findByCreatedDateLessThanEqual(LocalDate endDate);
}
