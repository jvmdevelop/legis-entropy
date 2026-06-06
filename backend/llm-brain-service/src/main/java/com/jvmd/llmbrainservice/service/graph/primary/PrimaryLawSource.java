package com.jvmd.llmbrainservice.service.graph.primary;


import com.jvmd.llmbrainservice.dto.LawInfo;

import java.util.List;

public interface PrimaryLawSource {

    int order();

    String name();

    boolean shouldRun(PrimaryLawContext ctx);

    List<LawInfo> collect(PrimaryLawContext ctx);
}
