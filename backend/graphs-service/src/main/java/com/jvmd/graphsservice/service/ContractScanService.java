package com.jvmd.graphsservice.service;

import com.jvmd.graphsservice.dto.CitationCheckDTO;
import com.jvmd.graphsservice.dto.ContractScanResponse;
import com.jvmd.graphsservice.model.Article;
import com.jvmd.graphsservice.parser.CitationExtractor;
import com.jvmd.graphsservice.parser.CitationExtractor.RawCitation;
import com.jvmd.graphsservice.repository.ArticleRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ContractScanService {

    private static final long OUTDATED_MONTHS = 6;

    private final CitationExtractor citationExtractor;
    private final ArticleRepository articleRepository;

    public ContractScanResponse scan(String text, String country) {
        if (country == null || country.isBlank()) country = "RK";
        List<RawCitation> raws = citationExtractor.extract(text);

        Map<String, RawCitation> unique = new LinkedHashMap<>();
        for (RawCitation r : raws) {
            String key =
                (r.inferredCode() == null ? "?" : r.inferredCode()) +
                "::" +
                r.number();
            unique.putIfAbsent(key, r);
        }

        List<CitationCheckDTO> checks = new ArrayList<>(unique.size());
        int found = 0,
            missing = 0,
            unknownCode = 0;
        LocalDateTime now = LocalDateTime.now();

        for (RawCitation r : unique.values()) {
            CitationCheckDTO.CitationCheckDTOBuilder b =
                CitationCheckDTO.builder()
                    .raw(r.raw())
                    .number(r.number())
                    .inferredCode(r.inferredCode())
                    .contextSnippet(r.contextSnippet());

            if (r.inferredCode() == null) {
                unknownCode++;
                checks.add(
                    b.foundInGraph(false).status("UNKNOWN_CODE").build()
                );
                continue;
            }

            Optional<Article> articleOpt =
                articleRepository.findByLawCodeAndNumber(
                    r.inferredCode(),
                    country,
                    r.number()
                );
            if (articleOpt.isEmpty()) {
                missing++;
                checks.add(b.foundInGraph(false).status("NOT_FOUND").build());
                continue;
            }

            Article a = articleOpt.get();
            found++;
            String status = "OK";
            LocalDate lastAm = a.getLastAmendmentDate();
            if (
                lastAm != null &&
                ChronoUnit.MONTHS.between(lastAm.atStartOfDay(), now) <=
                    OUTDATED_MONTHS
            ) {
                status = "RECENTLY_AMENDED";
            }
            checks.add(
                b
                    .foundInGraph(true)
                    .articleTitle(a.getTitle())
                    .lastAmendmentDate(lastAm)
                    .anchor(a.getAnchor())
                    .sourceUri(a.getSourceUri())
                    .status(status)
                    .build()
            );
        }

        log.info(
            "Contract scan: {} raw / {} unique citations, found={}, missing={}, unknownCode={}",
            raws.size(),
            unique.size(),
            found,
            missing,
            unknownCode
        );

        return ContractScanResponse.builder()
            .citations(checks)
            .totalCitations(unique.size())
            .totalFound(found)
            .totalMissing(missing)
            .totalUnknownCode(unknownCode)
            .build();
    }
}
