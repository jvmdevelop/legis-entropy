package com.jvmd.dms.law.repository;

import com.jvmd.dms.law.model.kz.KzLegislativeDocument;
import com.jvmd.dms.law.model.kz.KzLegislativeDocumentType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface KzLegislativeDocumentRepository extends JpaRepository<KzLegislativeDocument, UUID> {

    Optional<KzLegislativeDocument> findByCodeAndDocumentType(String code, KzLegislativeDocumentType type);

    List<KzLegislativeDocument> findByDocumentType(KzLegislativeDocumentType type);

    List<KzLegislativeDocument> findByDocumentTypeAndIsActiveTrue(KzLegislativeDocumentType type);

    List<KzLegislativeDocument> findByParentDocumentId(UUID parentId);

    @Query("SELECT d FROM KzLegislativeDocument d WHERE LOWER(d.title) LIKE LOWER(CONCAT('%', :title, '%')) AND d.isActive = true")
    Page<KzLegislativeDocument> searchByTitle(@Param("title") String title, Pageable pageable);

    @Query("SELECT d FROM KzLegislativeDocument d WHERE d.documentNumber = :number AND d.isActive = true")
    Optional<KzLegislativeDocument> findByDocumentNumber(@Param("number") String number);

    List<KzLegislativeDocument> findByDatePublishedBetween(LocalDate startDate, LocalDate endDate);

    @Query("SELECT d FROM KzLegislativeDocument d WHERE d.code = :code AND d.isActive = true")
    Optional<KzLegislativeDocument> findByCode(@Param("code") String code);

    @Query(value = "SELECT * FROM kz_legislative_documents WHERE doc_type = 'LAW' AND is_active = true", nativeQuery = true)
    List<KzLegislativeDocument> findAllActiveLaws();

    @Query(value = "SELECT * FROM kz_legislative_documents WHERE doc_type = 'CODE' AND is_active = true", nativeQuery = true)
    List<KzLegislativeDocument> findAllActiveCodes();
}
