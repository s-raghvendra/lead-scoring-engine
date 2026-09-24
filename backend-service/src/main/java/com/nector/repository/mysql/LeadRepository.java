package com.nector.repository.mysql;

import com.nector.model.mysql.Lead;
import com.nector.model.mysql.Lead.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * MySQL JPA repository for {@link Lead}.
 */
@Repository
public interface LeadRepository extends JpaRepository<Lead, Long> {

    Optional<Lead> findByEmail(String email);

    Optional<Lead> findByPhone(String phone);

    Optional<Lead> findByEmailOrPhone(String email, String phone);

    List<Lead> findByCategory(Category category);

    // ── Analytics queries ─────────────────────────────────────────────────

    @Query("SELECT l.category, COUNT(l) FROM Lead l WHERE l.category IS NOT NULL GROUP BY l.category")
    List<Object[]> countByCategory();

    @Query("SELECT l.source, AVG(l.latestScore) FROM Lead l WHERE l.latestScore IS NOT NULL GROUP BY l.source")
    List<Object[]> avgScoreBySource();

    @Query("SELECT l FROM Lead l WHERE l.category = 'HOT' ORDER BY l.latestScore DESC")
    List<Lead> findTopHotLeads(org.springframework.data.domain.Pageable pageable);

    long countByCategory(Category category);
}
