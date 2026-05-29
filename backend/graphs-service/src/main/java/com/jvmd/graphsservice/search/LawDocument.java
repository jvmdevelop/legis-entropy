package com.jvmd.graphsservice.search;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Setting;

@Document(indexName = "laws")
@Setting(settingPath = "elasticsearch/law-settings.json")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LawDocument {

    @Id
    private String id;

    @Field(type = FieldType.Keyword)
    private String citationCode;

    @Field(type = FieldType.Keyword)
    private String code;

    @Field(type = FieldType.Keyword)
    private String country;

    @Field(type = FieldType.Keyword)
    private String status;

    @Field(type = FieldType.Keyword)
    private String type;

    @Field(type = FieldType.Keyword)
    private String legalForce;

    @Field(type = FieldType.Text, analyzer = "russian_legal", searchAnalyzer = "russian_legal")
    private String title;

    @Field(type = FieldType.Text, analyzer = "kk_legal", searchAnalyzer = "kk_legal")
    private String titleKk;

    @Field(type = FieldType.Text, analyzer = "russian_legal", searchAnalyzer = "russian_legal")
    private String summary;

    @Field(type = FieldType.Text, analyzer = "kk_legal", searchAnalyzer = "kk_legal")
    private String summaryKk;

    @Field(type = FieldType.Text, analyzer = "russian_legal", searchAnalyzer = "russian_legal")
    private String subjectArea;

    @Field(type = FieldType.Integer)
    private int hierarchyRank;

    @Field(type = FieldType.Keyword)
    private String sourceUri;
}
