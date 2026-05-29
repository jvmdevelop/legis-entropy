package com.jvmd.graphsservice.search;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LawSearchRepository extends ElasticsearchRepository<LawDocument, String> {
}
