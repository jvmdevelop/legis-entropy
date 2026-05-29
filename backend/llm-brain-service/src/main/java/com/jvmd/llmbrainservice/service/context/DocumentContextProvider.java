package com.jvmd.llmbrainservice.service.context;

import com.jvmd.llmbrainservice.model.BrainRequest;

public interface DocumentContextProvider {

    DocumentContext load(BrainRequest request);
}
