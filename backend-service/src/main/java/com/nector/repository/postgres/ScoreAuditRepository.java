package com.nector.repository.postgres;

import com.nector.model.postgres.ScoreAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * PostgreSQL JPA repository for {@link ScoreAudit}.
 */
@Repository
public interface ScoreAuditRepository extends JpaRepository<ScoreAudit, Long> {

    List<ScoreAudit> findByLeadIdOrderByScoredAtDesc(Long leadId);
}
