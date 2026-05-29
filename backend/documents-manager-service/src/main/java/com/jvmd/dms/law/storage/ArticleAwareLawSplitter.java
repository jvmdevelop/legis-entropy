package com.jvmd.dms.law.storage;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ArticleAwareLawSplitter {

    private static final Pattern ARTICLE_PATTERN = Pattern.compile(
        "(?m)^(?:Статья|СТАТЬЯ|статья)\\s+(\\d+)",
        Pattern.MULTILINE | Pattern.CASE_INSENSITIVE
    );

    private static final Pattern CHAPTER_PATTERN = Pattern.compile(
        "(?m)^(?:ГЛАВА|Глава|глава)\\s+([IVX]+|[А-Я]+|\\d+)",
        Pattern.MULTILINE | Pattern.CASE_INSENSITIVE
    );

    private static final int TOKEN_WINDOW_SIZE = 1000;

    public List<Document> apply(List<Document> documents) {
        List<Document> result = new ArrayList<>();

        for (Document document : documents) {
            List<Document> chunked = splitByArticles(document);
            result.addAll(chunked);
        }

        return result;
    }

    private List<Document> splitByArticles(Document document) {
        String content = document.getText() != null ? document.getText() : "";
        if (content.isEmpty()) {
            return List.of(document);
        }

        List<ArticleBoundary> boundaries = findArticleBoundaries(content);

        if (boundaries.isEmpty()) {
            log.debug(
                "No articles found in document, falling back to TokenTextSplitter"
            );
            return new TokenTextSplitter().apply(List.of(document));
        }

        List<Document> articles = new ArrayList<>();
        for (int i = 0; i < boundaries.size(); i++) {
            ArticleBoundary current = boundaries.get(i);
            int endPos = (i + 1 < boundaries.size())
                ? boundaries.get(i + 1).charOffset
                : content.length();

            String articleText = content
                .substring(current.charOffset, endPos)
                .trim();
            if (articleText.isEmpty()) continue;

            List<Document> articleChunks = splitLargeArticle(
                articleText,
                current.articleNumber,
                current.articleTitle,
                current.chapterTitle,
                document.getMetadata(),
                articles.size()
            );

            articles.addAll(articleChunks);
        }

        return articles.isEmpty() ? List.of(document) : articles;
    }

    private List<ArticleBoundary> findArticleBoundaries(String content) {
        List<ArticleBoundary> boundaries = new ArrayList<>();
        Matcher articleMatcher = ARTICLE_PATTERN.matcher(content);
        String lastChapterTitle = "";

        int lastChapterPos = 0;
        Matcher chapterMatcher = CHAPTER_PATTERN.matcher(content);
        while (chapterMatcher.find()) {
            lastChapterTitle = chapterMatcher.group(0);
            lastChapterPos = chapterMatcher.start();
        }

        while (articleMatcher.find()) {
            int articleNumber = Integer.parseInt(articleMatcher.group(1));
            int charOffset = articleMatcher.start();

            String chapterTitle = findChapterTitleBefore(
                content,
                charOffset,
                lastChapterTitle
            );

            String articleTitle = extractArticleTitle(content, charOffset);

            boundaries.add(
                new ArticleBoundary(
                    articleNumber,
                    charOffset,
                    articleTitle,
                    chapterTitle
                )
            );
        }

        return boundaries;
    }

    private String findChapterTitleBefore(
        String content,
        int pos,
        String lastChapterTitle
    ) {
        int searchStart = Math.max(0, pos - 500);
        String section = content.substring(searchStart, pos);
        Matcher chapterMatcher = CHAPTER_PATTERN.matcher(section);
        String found = lastChapterTitle;

        while (chapterMatcher.find()) {
            found = chapterMatcher.group(0);
        }

        return found;
    }

    private String extractArticleTitle(String content, int articlePos) {
        int lineEnd = content.indexOf('\n', articlePos);
        if (lineEnd == -1) lineEnd = Math.min(
            articlePos + 200,
            content.length()
        );

        String line = content.substring(articlePos, lineEnd);
        String title = line
            .replaceAll("(?:Статья|СТАТЬЯ|статья)\\s+\\d+\\.?\\s*", "")
            .trim();
        if (title.length() > 120) {
            title = title.substring(0, 120);
        }

        return title;
    }

    private List<Document> splitLargeArticle(
        String articleText,
        int articleNumber,
        String articleTitle,
        String chapterTitle,
        Map<String, Object> baseMetadata,
        int globalChunkIndex
    ) {
        int estimatedTokens = articleText.length() / 4;

        if (estimatedTokens < TOKEN_WINDOW_SIZE) {
            return List.of(
                createArticleDocument(
                    articleText,
                    articleNumber,
                    articleTitle,
                    chapterTitle,
                    baseMetadata,
                    globalChunkIndex,
                    0
                )
            );
        }

        log.debug(
            "Article {} is large ({} tokens), splitting into sub-chunks",
            articleNumber,
            estimatedTokens
        );

        List<Document> chunks = new ArrayList<>();
        List<Document> tempChunks = new TokenTextSplitter().apply(
            List.of(new Document(articleText, new HashMap<>(baseMetadata)))
        );

        for (int i = 0; i < tempChunks.size(); i++) {
            Document chunk = tempChunks.get(i);
            Document enriched = createArticleDocument(
                chunk.getText(),
                articleNumber,
                articleTitle,
                chapterTitle,
                baseMetadata,
                globalChunkIndex + i,
                i
            );
            chunks.add(enriched);
        }

        return chunks;
    }

    private Document createArticleDocument(
        String text,
        int articleNumber,
        String articleTitle,
        String chapterTitle,
        Map<String, Object> baseMetadata,
        int chunkIndex,
        int articleChunkIndex
    ) {
        Map<String, Object> metadata = new HashMap<>(baseMetadata);
        metadata.put("articleNumber", articleNumber);
        metadata.put("articleTitle", articleTitle);
        if (!chapterTitle.isEmpty()) {
            metadata.put("chapterTitle", chapterTitle);
        }
        metadata.put("chunkIndex", chunkIndex);
        metadata.put("articleChunkIndex", articleChunkIndex);

        return new Document(text, metadata);
    }

    private static class ArticleBoundary {

        int articleNumber;
        int charOffset;
        String articleTitle;
        String chapterTitle;

        ArticleBoundary(
            int articleNumber,
            int charOffset,
            String articleTitle,
            String chapterTitle
        ) {
            this.articleNumber = articleNumber;
            this.charOffset = charOffset;
            this.articleTitle = articleTitle;
            this.chapterTitle = chapterTitle;
        }
    }
}
