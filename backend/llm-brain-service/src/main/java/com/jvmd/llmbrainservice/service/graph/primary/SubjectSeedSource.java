package com.jvmd.llmbrainservice.service.graph.primary;

import java.util.ArrayList;
import java.util.List;

import com.jvmd.llmbrainservice.dto.LawInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class SubjectSeedSource implements PrimaryLawSource {

    @Override
    public int order() {
        return 20;
    }

    @Override
    public String name() {
        return "subject-seed";
    }

    @Override
    public boolean shouldRun(PrimaryLawContext ctx) {
        return true;
    }

    @Override
    public List<LawInfo> collect(PrimaryLawContext ctx) {
        List<String> seeds = ctx
            .linker()
            .suggestedLawCodes(ctx.subject(), ctx.graphId());
        if (seeds.isEmpty()) return List.of();
        List<LawInfo> out = new ArrayList<>(seeds.size());
        for (String code : seeds) {
            if (code == null || code.isBlank()) continue;
            LawInfo law = new LawInfo();
            law.code(code);
            law.title(code);
            out.add(law);
        }
        return out;
    }
}
