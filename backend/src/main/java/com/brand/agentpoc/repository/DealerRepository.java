package com.brand.agentpoc.repository;

import com.brand.agentpoc.entity.Dealer;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DealerRepository extends JpaRepository<Dealer, Long> {
    List<Dealer> findByDealerCodeIgnoreCase(String dealerCode);

    List<Dealer> findByCityIgnoreCase(String city);

    List<Dealer> findByDealerGroupNameIgnoreCase(String dealerGroupName);
}
