package com.jvmd.dms.law.parser.impl.impl;

import com.jvmd.dms.law.parser.LawParserManager;
import com.jvmd.dms.law.storage.CategorizedLawStorage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;

import java.util.concurrent.BlockingDeque;

@Slf4j
public class KzParserManager extends LawParserManager<KzLawParser> {

    public KzParserManager(KzLawParser kzLawParser,
                          KzLawPathParser kzLawPathParser,
                          CategorizedLawStorage categorizedLawStorage,
                          @Qualifier("kzPathQueue") BlockingDeque<String> pathQueue,
                          @Value("${parser.kz.base-url:https://adilet.zan.kz}") String baseUrl,
                          @Value("${parser.kz.search-path:/rus/search/docs}") String searchPath) {
        super(kzLawParser, kzLawPathParser, categorizedLawStorage, pathQueue, baseUrl, searchPath);
    }

    @Override
    public String getQueueStatus() {
        return String.format("Kazakhstan parser queue size: %d", pathQueue.size());
    }
}
