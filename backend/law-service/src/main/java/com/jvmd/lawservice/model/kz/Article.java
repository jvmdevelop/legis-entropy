package com.jvmd.lawservice.model.kz;

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
@Table(name = "articles", indexes = {
        @Index(name = "idx_article_law_id", columnList = "legislative_document_id"),
        @Index(name = "idx_article_number", columnList = "article_number")
})
public class Article {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "legislative_document_id", nullable = false)
    private KzLegislativeDocument legislativeDocument;

    @Column(name = "article_number", nullable = false)
    private Integer articleNumber;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String fullText;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(nullable = false, columnDefinition = "INT DEFAULT 1")
    private Integer version = 1;

    private LocalDate changedAt;

    @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT true")
    private Boolean isActive = true;

    @OneToMany(mappedBy = "article", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Clause> clauses = new ArrayList<>();

    @Column(name = "created_at", nullable = false, columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private LocalDateTime updatedAt = LocalDateTime.now();

    public void addClause(Clause clause) {
        clauses.add(clause);
        clause.setArticle(this);
    }

    public String getFullReference() {
        return String.format("Статья %d %s", articleNumber, legislativeDocument.getTitle());
    }
}
