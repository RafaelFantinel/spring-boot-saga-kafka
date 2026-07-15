package com.refarch.orderservice.repository;

import com.refarch.orderservice.domain.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    @Query("select e from OutboxEvent e where e.published = false order by e.createdAt asc")
    List<OutboxEvent> findUnpublishedOrderByCreatedAtAsc(org.springframework.data.domain.Pageable pageable);
}
