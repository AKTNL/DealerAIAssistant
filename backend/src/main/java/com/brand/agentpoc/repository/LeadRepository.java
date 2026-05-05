package com.brand.agentpoc.repository;

import com.brand.agentpoc.entity.Lead;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeadRepository extends JpaRepository<Lead, Long> {
}
