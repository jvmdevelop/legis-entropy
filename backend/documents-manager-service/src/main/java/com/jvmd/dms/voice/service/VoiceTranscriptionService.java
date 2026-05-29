package com.jvmd.dms.voice.service;

import java.io.InputStream;

public interface VoiceTranscriptionService {

    TranscriptionResult transcribe(InputStream audio, String contentType, String languageHint);

    record Segment(String speaker, int startMs, int endMs, String text) {}

    record TranscriptionResult(String language,
                               int durationMs,
                               String plainText,
                               java.util.List<Segment> segments) {}
}
