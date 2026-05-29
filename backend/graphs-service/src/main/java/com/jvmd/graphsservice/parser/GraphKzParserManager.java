package com.jvmd.graphsservice.parser;

import com.jvmd.graphsservice.repository.LawRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.concurrent.BlockingDeque;

@Component
@Primary
@Slf4j
public class GraphKzParserManager extends GraphLawParserManager<GraphKzLawParser> {

    public GraphKzParserManager(GraphKzLawParser graphKzLawParser,
                               GraphKzLawPathParser graphKzLawPathParser,
                               LawRepository lawRepository,
                               @Qualifier("graphKzPathQueue") BlockingDeque<String> pathQueue,
                               @Value("${parser.kz.base-url:https://zan.gov.kz}") String baseUrl,
                               @Value("${parser.kz.search-path:/api/documents/search}") String searchPath) {
        super(graphKzLawParser, graphKzLawPathParser, lawRepository, pathQueue, baseUrl, searchPath);
    }

    @Override
    public String getQueueStatus() {
        return String.format("Kazakhstan graph parser queue size: %d", pathQueue.size());
    }
}
