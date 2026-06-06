package com.jvmd.llmbrainservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.Objects;

@Data
@Accessors(fluent = true)
@AllArgsConstructor
@NoArgsConstructor
public class LawInfo {
    private Long id;
    private String title;
    private String code;
    private String country;
    private String type;
    private String status;
    private String summary;
    @JsonProperty("adoptionDate")
    private String adoptionDate;
    @JsonProperty("effectiveDate")
    private String effectiveDate;
}