package com.jvmd.dms.voice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;

@Service
@ConditionalOnMissingBean(VoiceTranscriptionService.class)
@Slf4j
public class StubVoiceTranscriptionService implements VoiceTranscriptionService {

    @Override
    public TranscriptionResult transcribe(InputStream audio, String contentType, String languageHint) {
        log.warn("StubVoiceTranscriptionService active — no real STT configured. "
                + "Wire a Whisper/GoogleSTT bean to replace this.");
        String lang = languageHint != null ? languageHint : "ru";
        String text = "[Транскрипция недоступна: STT-сервис не подключён. "
                + "Загруженный файл сохранён, можно вручную добавить расшифровку.]";
        return new TranscriptionResult(
                lang,
                0,
                text,
                List.of(new Segment("SPEAKER_UNKNOWN", 0, 0, text))
        );
    }
}
