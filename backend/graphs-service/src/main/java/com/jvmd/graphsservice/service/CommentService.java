package com.jvmd.graphsservice.service;

import com.jvmd.graphsservice.dto.CommentDTO;
import com.jvmd.graphsservice.dto.UpsertCommentRequest;
import com.jvmd.graphsservice.model.Comment;
import com.jvmd.graphsservice.repository.CommentRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CommentService {

    private final CommentRepository repository;

    @Transactional
    public CommentDTO upsert(
        String graphId,
        String userId,
        UpsertCommentRequest req
    ) {
        String id = (req.getId() == null || req.getId().isBlank())
            ? "comment-" + UUID.randomUUID()
            : req.getId();
        Comment saved = repository
            .upsertInGraph(
                id,
                graphId,
                userId,
                req.getTitle(),
                req.getBody(),
                computePreview(req.getPreview(), req.getBody()),
                req.getKind(),
                req.getSubjectKind(),
                req.getSubjectId(),
                LocalDateTime.now()
            )
            .orElseThrow(() ->
                new IllegalStateException("Graph not found: " + graphId)
            );

        if (
            req.getSubjectKind() != null &&
            !req.getSubjectKind().isBlank() &&
            req.getSubjectId() != null &&
            !req.getSubjectId().isBlank()
        ) {
            try {
                repository.linkSubject(
                    id,
                    req.getSubjectKind(),
                    req.getSubjectId()
                );
            } catch (Exception e) {
                log.warn(
                    "Comment subject link failed for {} -> {}:{}: {}",
                    id,
                    req.getSubjectKind(),
                    req.getSubjectId(),
                    e.getMessage()
                );
            }
        }

        if (req.getReferencedLawCodes() != null) {
            for (String code : req.getReferencedLawCodes()) {
                if (code == null || code.isBlank()) continue;
                try {
                    repository.linkLaw(id, code, "RK");
                } catch (Exception e) {
                    log.debug(
                        "Comment law link failed for {} -> {}: {}",
                        id,
                        code,
                        e.getMessage()
                    );
                }
            }
        }

        if (req.getReferencedArticles() != null) {
            for (UpsertCommentRequest.ArticleRef ref : req.getReferencedArticles()) {
                if (
                    ref == null ||
                    ref.getLawCode() == null ||
                    ref.getNumber() == null
                ) continue;
                String country = (ref.getCountry() == null ||
                    ref.getCountry().isBlank())
                    ? "RK"
                    : ref.getCountry();
                try {
                    repository.linkArticle(
                        id,
                        ref.getLawCode(),
                        country,
                        ref.getNumber(),
                        ref.getReason()
                    );
                } catch (Exception e) {
                    log.debug(
                        "Comment article link failed for {} -> {} ст. {}: {}",
                        id,
                        ref.getLawCode(),
                        ref.getNumber(),
                        e.getMessage()
                    );
                }
            }
        }

        log.info(
            "Upserted comment {} into graph {} (kind={}, laws={}, articles={})",
            id,
            graphId,
            req.getKind(),
            req.getReferencedLawCodes() == null
                ? 0
                : req.getReferencedLawCodes().size(),
            req.getReferencedArticles() == null
                ? 0
                : req.getReferencedArticles().size()
        );
        return CommentDTO.from(saved);
    }

    public List<CommentDTO> listByGraph(String graphId) {
        return repository
            .findByGraphId(graphId)
            .stream()
            .map(CommentDTO::from)
            .toList();
    }

    public Optional<CommentDTO> get(String id) {
        return repository.findById(id).map(CommentDTO::from);
    }

    public void delete(String id) {
        repository.deleteByIdCascade(id);
    }

    private static String computePreview(String explicit, String body) {
        if (explicit != null && !explicit.isBlank()) return explicit;
        if (body == null) return "";
        String stripped = body
            .replaceAll("[#*_>`-]+", " ")
            .replaceAll("\\s+", " ")
            .trim();
        return stripped.length() <= 220
            ? stripped
            : stripped.substring(0, 217) + "…";
    }
}
