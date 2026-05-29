package com.jvmd.graphsservice.parser;

import com.jvmd.graphsservice.model.Law;

public interface Parser<T extends Law> {
    T parse(String url);
}
