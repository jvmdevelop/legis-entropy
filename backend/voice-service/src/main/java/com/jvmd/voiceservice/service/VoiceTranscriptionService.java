package com.jvmd.voiceservice.service;

import java.io.InputStream;
import java.util.List;

public interface VoiceTranscriptionService {

    TranscriptionResult transcribe(InputStream audio, String contentType, String languageHint);

    record Segment(String speaker, int startMs, int endMs, String text) {}

    record TranscriptionResult(String language,
                               int durationMs,
                               String plainText,
                               List<Segment> segments) {}
}
