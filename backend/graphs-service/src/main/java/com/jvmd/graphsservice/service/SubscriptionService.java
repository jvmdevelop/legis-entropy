package com.jvmd.graphsservice.service;

import com.jvmd.graphsservice.dto.ChangeEventDTO;
import com.jvmd.graphsservice.dto.CreateSubscriptionRequest;
import com.jvmd.graphsservice.dto.SubscriptionDTO;
import com.jvmd.graphsservice.model.Article;
import com.jvmd.graphsservice.model.ChangeEvent;
import com.jvmd.graphsservice.model.Subscription;
import com.jvmd.graphsservice.repository.ArticleRepository;
import com.jvmd.graphsservice.repository.ChangeEventRepository;
import com.jvmd.graphsservice.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final ChangeEventRepository changeEventRepository;
    private final ArticleRepository articleRepository;

    public SubscriptionDTO create(String userId, CreateSubscriptionRequest req) {
        String country = req.getCountry() == null || req.getCountry().isBlank() ? "RK" : req.getCountry();
        Optional<Subscription> existing = subscriptionRepository.findOne(
                userId, req.getLawCode(), country, req.getArticleNumber());
        if (existing.isPresent()) {
            Subscription s = existing.get();
            String title = articleTitle(s.getLawCode(), s.getCountry(), s.getArticleNumber());
            return SubscriptionDTO.from(s, title, 0);
        }

        Optional<Article> articleOpt = articleRepository.findByLawCodeAndNumber(
                req.getLawCode(), country, req.getArticleNumber());

        Subscription s = new Subscription();
        s.setId(UUID.randomUUID().toString());
        s.setUserId(userId);
        s.setLawCode(req.getLawCode());
        s.setCountry(country);
        s.setArticleNumber(req.getArticleNumber());
        s.setCreatedAt(LocalDateTime.now());
        s.setLastCheckedAt(LocalDateTime.now());
        s.setLastSnapshotHash(articleOpt.map(SubscriptionService::hashArticle).orElse(null));
        subscriptionRepository.save(s);

        String title = articleOpt.map(Article::getTitle).orElse(null);
        return SubscriptionDTO.from(s, title, 0);
    }

    public List<SubscriptionDTO> list(String userId) {
        return subscriptionRepository.findByUser(userId).stream()
                .map(s -> {
                    String title = articleTitle(s.getLawCode(), s.getCountry(), s.getArticleNumber());
                    int unread = (int) changeEventRepository.findBySubscription(s.getId()).stream()
                            .filter(e -> !e.isAcknowledged()).count();
                    return SubscriptionDTO.from(s, title, unread);
                })
                .toList();
    }

    public void delete(String userId, String subscriptionId) {
        subscriptionRepository.findById(subscriptionId).ifPresent(s -> {
            if (userId.equals(s.getUserId())) {
                subscriptionRepository.deleteById(subscriptionId);
            }
        });
    }

    public List<ChangeEventDTO> recentEvents(String userId) {
        return changeEventRepository.findRecentByUser(userId).stream()
                .map(ChangeEventDTO::from)
                .toList();
    }

    public int unreadCount(String userId) {
        return changeEventRepository.countUnreadByUser(userId);
    }

    public void acknowledgeAll(String userId) {
        changeEventRepository.acknowledgeAll(userId);
    }

    @Scheduled(fixedDelay = 3_600_000L, initialDelay = 60_000L)
    public void checkAll() {
        List<Subscription> all = subscriptionRepository.findAllActive();
        if (all.isEmpty()) return;
        int changes = 0;
        LocalDateTime now = LocalDateTime.now();
        for (Subscription s : all) {
            try {
                Optional<Article> articleOpt = articleRepository.findByLawCodeAndNumber(
                        s.getLawCode(), s.getCountry(), s.getArticleNumber());
                if (articleOpt.isEmpty()) {
                    s.setLastCheckedAt(now);
                    subscriptionRepository.save(s);
                    continue;
                }
                Article a = articleOpt.get();
                String newHash = hashArticle(a);
                String oldHash = s.getLastSnapshotHash();
                if (oldHash != null && !oldHash.equals(newHash)) {
                    ChangeEvent ev = new ChangeEvent();
                    ev.setId(UUID.randomUUID().toString());
                    ev.setSubscriptionId(s.getId());
                    ev.setUserId(s.getUserId());
                    ev.setLawCode(s.getLawCode());
                    ev.setCountry(s.getCountry());
                    ev.setArticleNumber(s.getArticleNumber());
                    ev.setOldHash(oldHash);
                    ev.setNewHash(newHash);
                    ev.setNewAmendmentDate(a.getLastAmendmentDate());
                    ev.setDetectedAt(now);
                    ev.setAcknowledged(false);
                    changeEventRepository.save(ev);
                    changes++;
                }
                s.setLastSnapshotHash(newHash);
                s.setLastCheckedAt(now);
                subscriptionRepository.save(s);
            } catch (Exception e) {
                log.warn("Subscription check failed for {}: {}", s.getId(), e.getMessage());
            }
        }
        if (changes > 0) {
            log.info("Law Watcher: detected {} change(s) across {} subscriptions", changes, all.size());
        }
    }

    private String articleTitle(String lawCode, String country, String articleNumber) {
        return articleRepository.findByLawCodeAndNumber(lawCode, country, articleNumber)
                .map(Article::getTitle).orElse(null);
    }

    private static String hashArticle(Article a) {
        String body = a.getBody() == null ? "" : a.getBody();
        String stamp = a.getLastAmendmentDate() == null ? "" : a.getLastAmendmentDate().toString();
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest((body + "::" + stamp).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString((body + stamp).hashCode());
        }
    }
}
