package com.flightmonitor.core.search.entity;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SearchRunRepository extends JpaRepository<SearchRun, Long> {

    List<SearchRun> findByMonitorIdOrderByStartedAtDescIdDesc(Long monitorId, Pageable pageable);

    /** Taxa de falha por fonte de preco — diagnostico de provider degradado. */
    long countBySourceAndStatusAndStartedAtAfter(PriceSource source, SearchStatus status, Instant desde);
}
