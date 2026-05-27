package com.brand.agentpoc.repository;

import com.brand.agentpoc.entity.Target;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TargetRepository extends JpaRepository<Target, Long> {
    List<Target> findByDealerCodeIgnoreCase(String dealerCode);

    List<Target> findByCityIgnoreCase(String city);

    List<Target> findByDealerGroupNameIgnoreCase(String dealerGroupName);

    List<Target> findByProductModelIgnoreCase(String productModel);

    List<Target> findByTargetYear(Integer targetYear);

    List<Target> findByTargetYearAndTargetMonth(Integer targetYear, Integer targetMonth);
}
