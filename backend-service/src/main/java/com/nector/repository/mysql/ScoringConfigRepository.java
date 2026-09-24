package com.nector.repository.mysql;

import com.nector.model.mysql.ScoringConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * MySQL JPA repository for {@link ScoringConfig}.
 */
@Repository
public interface ScoringConfigRepository extends JpaRepository<ScoringConfig, Long> {

    /** Returns only the active rules used by the scoring engine. */
    List<ScoringConfig> findByIsActiveTrue();
}
