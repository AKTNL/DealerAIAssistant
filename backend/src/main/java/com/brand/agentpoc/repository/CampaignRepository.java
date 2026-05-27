package com.brand.agentpoc.repository;

import com.brand.agentpoc.entity.Campaign;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CampaignRepository extends JpaRepository<Campaign, Long> {
    List<Campaign> findByCampaignTypeIgnoreCase(String campaignType);

    List<Campaign> findByDealerCodeIgnoreCase(String dealerCode);

    List<Campaign> findByCityIgnoreCase(String city);

    List<Campaign> findByDealerGroupNameIgnoreCase(String dealerGroupName);

    List<Campaign> findByProductModelIgnoreCase(String productModel);

    List<Campaign> findByCreatedDateBetween(LocalDate startDate, LocalDate endDate);
}
