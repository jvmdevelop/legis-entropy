package com.jvmd.llmbrainservice.service.graph.link;

import com.jvmd.llmbrainservice.model.BrainRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Component
@Slf4j
public class SubjectLinkerRegistry {

    private final List<SubjectLinker> linkers;

    public SubjectLinkerRegistry(List<SubjectLinker> linkers) {
        this.linkers = linkers.stream()
                .sorted(Comparator.comparingInt(SubjectLinker::priority))
                .toList();
        log.info("SubjectLinkerRegistry initialised with {} linker(s): {}",
                this.linkers.size(),
                this.linkers.stream().map(l -> l.kind().name() + "@" + l.priority()).toList());
    }

    public Optional<SubjectLinker> selectFor(BrainRequest request) {
        for (SubjectLinker linker : linkers) {
            if (linker.canHandle(request)) return Optional.of(linker);
        }
        return Optional.empty();
    }

    public Optional<SubjectLinker> byKind(SubjectKind kind) {
        for (SubjectLinker linker : linkers) {
            if (linker.kind() == kind) return Optional.of(linker);
        }
        return Optional.empty();
    }

    public List<SubjectLinker> all() { return linkers; }
}
