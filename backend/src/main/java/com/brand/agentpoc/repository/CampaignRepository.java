package com.brand.agentpoc.repository;

import com.brand.agentpoc.entity.Campaign;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CampaignRepository extends JpaRepository<Campaign, Long> {
}
