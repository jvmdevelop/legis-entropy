package com.jvmd.llmbrainservice.client;

import com.jvmd.llmbrainservice.model.LawArticleInfo;
import com.jvmd.llmbrainservice.model.LawArticleTitle;
import com.jvmd.llmbrainservice.model.RetrievalChunkResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "law-service")
public interface LawClient {

    @GetMapping("/api/laws/search")
    List<RetrievalChunkResponse> searchLaws(@RequestParam("query") String query);

    @GetMapping("/api/laws/search/{country}")
    List<RetrievalChunkResponse> searchLawsByCountry(@PathVariable("country") String country, @RequestParam("query") String query);

    @GetMapping("/api/laws/articles/lookup")
    List<LawArticleInfo> lookupArticles(@RequestParam("lawCode") String lawCode,
                                        @RequestParam("numbers") List<String> numbers,
                                        @RequestParam("country") String country);

    @GetMapping("/api/laws/articles/titles")
    List<LawArticleTitle> articleTitles(@RequestParam("lawCode") String lawCode,
                                        @RequestParam("country") String country);
}
