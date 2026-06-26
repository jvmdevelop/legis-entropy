package com.jvmd.llmbrainservice.service.prompt;

import com.jvmd.llmbrainservice.model.BrainRequest;
import com.jvmd.llmbrainservice.service.context.DocumentContext;
import com.jvmd.llmbrainservice.service.graph.link.LinkableSubject;
import com.jvmd.llmbrainservice.service.graph.link.SubjectLinker;
import com.jvmd.llmbrainservice.service.graph.link.SubjectLinkerRegistry;
import com.jvmd.llmbrainservice.service.pipeline.BrainTaskType;
import com.jvmd.llmbrainservice.service.pipeline.ContextPlan;
import com.jvmd.llmbrainservice.service.pipeline.ProfessionProfile;
import com.jvmd.llmbrainservice.service.pipeline.RetrievalMode;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class BrainPromptBuilder {

    private final AssistantInstructions instructions;
    private final SubjectLinkerRegistry linkerRegistry;

    public String buildUserPrompt(
        BrainRequest request,
        DocumentContext documentContext,
        ContextPlan plan,
        ProfessionProfile profession
    ) {
        boolean hasContext =
            documentContext != null &&
            !documentContext.unavailable() &&
            documentContext.text() != null &&
            !documentContext.text().isBlank();

        boolean isDocumentTask = plan.taskType().isDocumentTask();

        String taskInstruction = (isDocumentTask && hasContext)
            ? instructions.documentAnalysisInstruction()
            : instructions.generalAdviceInstruction();

        String professionBlock = buildProfessionBlock(profession);
        String toolReminder = buildToolReminder(request.message());
        String contextAwareInstruction = buildContextAwareInstruction(
            hasContext,
            plan
        );
        String graphBlock = buildGraphBlock(request);
        String subjectBlock = buildSubjectContextBlock(request);

        if (hasContext) {
            return String.format(
                """
                Контекст документа:
                %s

                %sВопрос пользователя:
                %s

                %s%s%s%sИнструкция:
                %s
                """,
                documentContext.text(),
                subjectBlock,
                request.message(),
                graphBlock,
                professionBlock,
                toolReminder,
                contextAwareInstruction,
                taskInstruction
            );
        }

        return String.format(
            """
            %sВопрос пользователя:
            %s

            %s%s%sИнструкция:
            %s
            """,
            subjectBlock,
            request.message(),
            graphBlock,
            professionBlock,
            toolReminder,
            taskInstruction
        );
    }

    private String buildSubjectContextBlock(BrainRequest request) {
        if (!request.hasGraphId()) return "";
        Optional<SubjectLinker> linkerOpt = linkerRegistry.selectFor(request);
        if (linkerOpt.isEmpty()) return "";
        SubjectLinker linker = linkerOpt.get();
        Optional<LinkableSubject> subjectOpt = linker.resolve(request);
        if (subjectOpt.isEmpty()) return "";
        LinkableSubject subject = subjectOpt.get();
        String text;
        try {
            text = linker.fetchSearchableText(subject, request.graphId());
        } catch (Exception e) {
            log.debug(
                "Could not load subject text for {} {}: {}",
                linker.kind(),
                subject.id(),
                e.getMessage()
            );
            return "";
        }
        if (text == null || text.isBlank()) return "";
        if (text.length() > 4000) text =
            text.substring(0, 4000) + "\n…(обрезано)";

        String label = linker.displayName();
        String header =
            subject.label() == null || subject.label().isBlank()
                ? label
                : label + " «" + subject.label() + "»";

        return (
            "Активный " +
            linker.displayNameAccusative() +
            " (используй для ответа):\n" +
            "— " +
            header +
            " —\n" +
            text +
            "\n— конец —\n\n"
        );
    }

    private String buildGraphBlock(BrainRequest request) {
        if (!request.hasGraphId()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append(
            "АКТИВНЫЙ КОНТЕКСТ (подставляй ЭТИ значения как параметры инструментов, не спрашивай их у пользователя):\n"
        );
        sb.append("- graphId = ").append(request.graphId()).append("\n");
        if (request.hasDocumentId()) {
            sb.append("- documentId = ")
                .append(request.documentId())
                .append("\n");
        }
        if (request.hasSituationId()) {
            sb.append("- situationId = ")
                .append(request.situationId())
                .append("\n");
        }
        if (request.hasVoiceId()) {
            sb.append("- voiceId = ").append(request.voiceId()).append("\n");
        }
        if (request.userId() != null && !request.userId().isBlank()) {
            sb.append("- userId = ").append(request.userId()).append("\n");
        }
        sb.append("\n");

        sb.append(
            "ОБЯЗАТЕЛЬНЫЕ ДЕЙСТВИЯ — при этих формулировках СРАЗУ вызови инструмент, НЕ описывай план:\n"
        );
        if (request.hasDocumentId()) {
            sb.append(
                "- «свяжи мой документ с законами / нормами / применимыми статьями», «привяжи документ к законам», «найди применимые к документу нормы» → ВЫЗОВИ linkDocumentToLawsInGraph(graphId, documentId, userId). Документ уже загружен — его ID выше. НЕ проси пользователя «предоставить документ».\n"
            );
        } else if (request.hasSituationId() || request.hasVoiceId()) {
            sb.append(
                "- «свяжи ситуацию с законами», «свяжи ГС с законами», «найди применимые нормы» — backend сам подберёт законы по тексту субъекта (детерминированно). Не описывай план, дождись результата.\n"
            );
        } else {
            sb.append(
                "- если пользователь просит связать документ/ситуацию, но ни documentId/situationId/voiceId выше НЕ указаны — ответь ОДНОЙ строкой: «Объект не привязан к графу — выберите ноду или загрузите файл». Никаких многошаговых планов.\n"
            );
        }
        sb.append(
            "- «добавь закон X в граф», «помести закон в граф» → ВЫЗОВИ addLawToUserGraph(graphId, code, country).\n"
        );
        sb.append(
            "- «найди и добавь законы про X», «подбери нормы по теме X в граф» → ВЫЗОВИ findAndAddRelatedLawsToGraph(graphId, query, country, limit).\n"
        );
        sb.append(
            "После вызова инструмента кратко перечисли, что было добавлено/связано. НЕ выводи JSON параметров и НЕ пиши «что нужно сделать» — это уже сделано тобой.\n\n"
        );

        return sb.toString();
    }

    private String buildToolReminder(String message) {
        boolean isAboutLaws =
            message.toLowerCase().contains("закон") ||
            message.toLowerCase().contains("статья") ||
            message.toLowerCase().contains("кодекс") ||
            message.toLowerCase().contains("норм") ||
            message.toLowerCase().contains("требование") ||
            message.toLowerCase().contains("срок") ||
            message.toLowerCase().contains("обязанность") ||
            message.toLowerCase().contains("право");

        if (isAboutLaws) {
            return "⚠️ Вопрос касается конкретных норм/законов. ОБЯЗАТЕЛЬНО используй searchLaws() для поиска точного текста.\n\n";
        }
        return "";
    }

    private String buildProfessionBlock(ProfessionProfile profile) {
        return "Профиль специализации:\n" + profile.getInstruction() + "\n";
    }

    private String buildContextAwareInstruction(
        boolean hasContext,
        ContextPlan plan
    ) {
        boolean isLawTask =
            plan.retrievalMode() == RetrievalMode.LAW ||
            plan.retrievalMode() == RetrievalMode.HYBRID_LEGAL;

        if (hasContext && isLawTask) {
            return "Выше уже предоставлен контекст нормативной базы. Используй searchLaws() ТОЛЬКО если нужна информация, которой нет в предоставленном контексте — другие статьи или связанные законы.\n\n";
        }

        return "";
    }
}
