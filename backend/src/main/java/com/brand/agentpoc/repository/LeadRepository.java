package com.brand.agentpoc.repository;

import com.brand.agentpoc.entity.Lead;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeadRepository extends JpaRepository<Lead, Long> {
    List<Lead> findByDealerCodeIgnoreCase(String dealerCode);

    List<Lead> findByLeadSourceIgnoreCase(String leadSource);

    List<Lead> findByLeadSourceIgnoreCaseAndDealerCodeIgnoreCase(String leadSource, String dealerCode);

    List<Lead> findByCityIgnoreCase(String city);

    List<Lead> findByDealerGroupNameIgnoreCase(String dealerGroupName);

    List<Lead> findByStageNameIgnoreCase(String stageName);

    List<Lead> findByProductModelIgnoreCase(String productModel);

    List<Lead> findByConverted(Boolean converted);

    List<Lead> findByCreatedDateBetween(LocalDate startDate, LocalDate endDate);
}
