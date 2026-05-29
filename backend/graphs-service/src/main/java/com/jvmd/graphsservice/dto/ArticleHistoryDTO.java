package com.jvmd.graphsservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleHistoryDTO {
    private ArticleDTO article;
    private List<AmendmentEdgeDTO> amendments;

    private LocalDate lastAmendmentDate;
}
