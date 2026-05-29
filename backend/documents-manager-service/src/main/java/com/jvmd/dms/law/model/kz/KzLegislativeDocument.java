package com.jvmd.dms.law.model.kz;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "doc_type")
@Table(name = "kz_legislative_documents")
public abstract class KzLegislativeDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private KzLegislativeDocumentType documentType;

    @Column(columnDefinition = "TEXT")
    private String fullText;

    private String code;

    private LocalDate datePublished;

    private String documentNumber;

    private String issuedBy;

    private LocalDate dateEnforced;

    private LocalDate dateExpired;

    @Column(name = "version", nullable = false, columnDefinition = "INT DEFAULT 1")
    private Integer version = 1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_document_id")
    private KzLegislativeDocument parentDocument;

    @OneToMany(mappedBy = "parentDocument", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<KzLegislativeDocument> amendments = new ArrayList<>();

    @OneToMany(mappedBy = "legislativeDocument", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Article> articles = new ArrayList<>();

    @Column(name = "created_at", nullable = false, columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT true")
    private Boolean isActive = true;

    public abstract String getHierarchyLevel();

    public void addArticle(Article article) {
        articles.add(article);
        article.setLegislativeDocument(this);
    }

    public void addAmendment(KzLegislativeDocument amendment) {
        amendments.add(amendment);
        amendment.setParentDocument(this);
    }
}
