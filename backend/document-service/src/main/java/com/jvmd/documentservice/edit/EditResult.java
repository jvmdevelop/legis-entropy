package com.jvmd.documentservice.edit;

import java.util.List;

public record EditResult(byte[] content, List<Integer> applied, List<Skipped> skipped) {

    public record Skipped(int index, String reason) {}
}
