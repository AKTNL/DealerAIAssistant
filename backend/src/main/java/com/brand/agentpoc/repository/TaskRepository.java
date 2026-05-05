package com.brand.agentpoc.repository;

import com.brand.agentpoc.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskRepository extends JpaRepository<Task, Long> {
}
