package com.jvmd.lawservice.service;

import com.jvmd.lawservice.dto.ArticleResponseDto;
import com.jvmd.lawservice.dto.LawResponseDto;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LawService {

    private final JdbcTemplate jdbcTemplate;

    public Page<LawResponseDto> searchLaws(String query, Pageable pageable) {
        String sql =
            "SELECT * FROM kz_legislative_documents WHERE LOWER(title) LIKE LOWER(?) AND is_active = true ORDER BY title LIMIT ? OFFSET ?";

        String searchQuery = "%" + query + "%";
        List<LawResponseDto> laws = jdbcTemplate.query(
            sql,
            lawMapper(),
            searchQuery,
            pageable.getPageSize(),
            pageable.getOffset()
        );

        String countSql =
            "SELECT COUNT(*) FROM kz_legislative_documents WHERE LOWER(title) LIKE LOWER(?) AND is_active = true";
        Long total = jdbcTemplate.queryForObject(
            countSql,
            Long.class,
            searchQuery
        );

        return new PageImpl<>(laws, pageable, total != null ? total : 0);
    }

    public Optional<LawResponseDto> getLawByCode(String code) {
        String sql =
            "SELECT * FROM kz_legislative_documents WHERE code = ? AND is_active = true LIMIT 1";
        List<LawResponseDto> results = jdbcTemplate.query(
            sql,
            lawMapper(),
            code
        );
        return results.isEmpty()
            ? Optional.empty()
            : Optional.of(results.get(0));
    }

    public Optional<LawResponseDto> getLawById(UUID id) {
        String sql =
            "SELECT * FROM kz_legislative_documents WHERE id = ? AND is_active = true LIMIT 1";
        List<LawResponseDto> results = jdbcTemplate.query(
            sql,
            lawMapper(),
            id.toString()
        );
        return results.isEmpty()
            ? Optional.empty()
            : Optional.of(results.get(0));
    }

    public List<LawResponseDto> getConstitutionAndCodes() {
        String sql =
            "SELECT * FROM kz_legislative_documents WHERE document_type IN ('CONSTITUTION', 'CODE') AND is_active = true ORDER BY document_type, title";
        return jdbcTemplate.query(sql, lawMapper());
    }

    public List<LawResponseDto> getAllCodes() {
        String sql =
            "SELECT * FROM kz_legislative_documents WHERE document_type = 'CODE' AND is_active = true ORDER BY code";
        return jdbcTemplate.query(sql, lawMapper());
    }

    public List<LawResponseDto> getAllLaws() {
        String sql =
            "SELECT * FROM kz_legislative_documents WHERE document_type = 'LAW' AND is_active = true ORDER BY title LIMIT 500";
        return jdbcTemplate.query(sql, lawMapper());
    }

    public List<ArticleResponseDto> getArticles(
        UUID lawId,
        boolean activeOnly
    ) {
        String sql =
            "SELECT a.id, a.article_number, a.title, a.full_text, a.notes, " +
            "d.code as law_code, d.title as law_title, a.version, a.changed_at, a.is_active, " +
            "(SELECT COUNT(*) FROM clauses WHERE article_id = a.id) as clause_count, " +
            "a.created_at, a.updated_at " +
            "FROM articles a " +
            "JOIN kz_legislative_documents d ON a.legislative_document_id = d.id " +
            "WHERE a.legislative_document_id = ?";

        if (activeOnly) {
            sql += " AND a.is_active = true";
        }

        sql += " ORDER BY a.article_number";

        return jdbcTemplate.query(sql, articleMapper(), lawId.toString());
    }

    public Optional<ArticleResponseDto> getArticle(
        String lawCode,
        Integer articleNumber
    ) {
        String sql =
            "SELECT a.id, a.article_number, a.title, a.full_text, a.notes, " +
            "d.code as law_code, d.title as law_title, a.version, a.changed_at, a.is_active, " +
            "(SELECT COUNT(*) FROM clauses WHERE article_id = a.id) as clause_count, " +
            "a.created_at, a.updated_at " +
            "FROM articles a " +
            "JOIN kz_legislative_documents d ON a.legislative_document_id = d.id " +
            "WHERE d.code = ? AND a.article_number = ? AND a.is_active = true LIMIT 1";

        List<ArticleResponseDto> results = jdbcTemplate.query(
            sql,
            articleMapper(),
            lawCode,
            articleNumber
        );
        return results.isEmpty()
            ? Optional.empty()
            : Optional.of(results.get(0));
    }

    public List<ArticleResponseDto> searchArticles(String keyword, int limit) {
        String sql =
            "SELECT a.id, a.article_number, a.title, a.full_text, a.notes, " +
            "d.code as law_code, d.title as law_title, a.version, a.changed_at, a.is_active, " +
            "(SELECT COUNT(*) FROM clauses WHERE article_id = a.id) as clause_count, " +
            "a.created_at, a.updated_at " +
            "FROM articles a " +
            "JOIN kz_legislative_documents d ON a.legislative_document_id = d.id " +
            "WHERE (LOWER(a.title) LIKE LOWER(?) OR LOWER(a.full_text) LIKE LOWER(?)) AND a.is_active = true " +
            "ORDER BY a.article_number LIMIT ?";

        String searchQuery = "%" + keyword + "%";
        return jdbcTemplate.query(
            sql,
            articleMapper(),
            searchQuery,
            searchQuery,
            limit
        );
    }

    public Map<String, Object> getHierarchyInfo(UUID lawId) {
        String sql =
            "SELECT id, title, document_type, code, version, " +
            "(SELECT COUNT(*) FROM articles WHERE legislative_document_id = kz_legislative_documents.id AND is_active = true) as article_count " +
            "FROM kz_legislative_documents WHERE id = ?";

        List<Map<String, Object>> results = jdbcTemplate.queryForList(
            sql,
            lawId.toString()
        );

        if (results.isEmpty()) {
            throw new IllegalArgumentException("Law not found: " + lawId);
        }

        return results.get(0);
    }

    public List<LawResponseDto> getAmendments(UUID lawId) {
        String sql =
            "SELECT * FROM kz_legislative_documents WHERE parent_document_id = ? AND document_type = 'AMENDMENT' ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, lawMapper(), lawId.toString());
    }

    public Long getTotalLawsCount() {
        String sql =
            "SELECT COUNT(*) FROM kz_legislative_documents WHERE is_active = true";
        Long count = jdbcTemplate.queryForObject(sql, Long.class);
        return count != null ? count : 0;
    }

    public Map<String, Long> getStatsByType() {
        String sql =
            "SELECT document_type, COUNT(*) as count FROM kz_legislative_documents WHERE is_active = true GROUP BY document_type";
        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql);

        Map<String, Long> stats = new HashMap<>();
        for (Map<String, Object> row : results) {
            String type = (String) row.get("document_type");
            Long count = ((Number) row.get("count")).longValue();
            stats.put(type, count);
        }
        return stats;
    }

    private RowMapper<LawResponseDto> lawMapper() {
        return (rs, rowNum) ->
            LawResponseDto.builder()
                .id(UUID.fromString(rs.getString("id")))
                .title(rs.getString("title"))
                .code(rs.getString("code"))
                .documentType(rs.getString("document_type"))
                .documentNumber(rs.getString("document_number"))
                .datePublished(
                    rs.getDate("date_published") != null
                        ? rs.getDate("date_published").toLocalDate()
                        : null
                )
                .issuedBy(rs.getString("issued_by"))
                .version(rs.getInt("version"))
                .hierarchyLevel("")
                .articleCount(0)
                .isActive(rs.getBoolean("is_active"))
                .createdAt(
                    rs.getTimestamp("created_at") != null
                        ? rs.getTimestamp("created_at").toLocalDateTime()
                        : LocalDateTime.now()
                )
                .updatedAt(
                    rs.getTimestamp("updated_at") != null
                        ? rs.getTimestamp("updated_at").toLocalDateTime()
                        : LocalDateTime.now()
                )
                .build();
    }

    private RowMapper<ArticleResponseDto> articleMapper() {
        return (rs, rowNum) ->
            ArticleResponseDto.builder()
                .id(UUID.fromString(rs.getString("id")))
                .articleNumber(rs.getInt("article_number"))
                .title(rs.getString("title"))
                .fullText(rs.getString("full_text"))
                .notes(rs.getString("notes"))
                .lawCode(rs.getString("law_code"))
                .lawTitle(rs.getString("law_title"))
                .version(rs.getInt("version"))
                .changedAt(
                    rs.getDate("changed_at") != null
                        ? rs.getDate("changed_at").toLocalDate()
                        : null
                )
                .isActive(rs.getBoolean("is_active"))
                .clauseCount(rs.getInt("clause_count"))
                .createdAt(
                    rs.getTimestamp("created_at") != null
                        ? rs.getTimestamp("created_at").toLocalDateTime()
                        : LocalDateTime.now()
                )
                .updatedAt(
                    rs.getTimestamp("updated_at") != null
                        ? rs.getTimestamp("updated_at").toLocalDateTime()
                        : LocalDateTime.now()
                )
                .build();
    }
}
