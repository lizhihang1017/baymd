package com.zhli.baymd.rag.core.memory.extract;

import com.zhli.baymd.infra.embedding.EmbeddingService;
import com.zhli.baymd.rag.core.followup.FollowUpPlanService;
import com.zhli.baymd.rag.core.followup.FollowUpPlanService.FollowUpPlan;
import com.zhli.baymd.rag.core.memory.evolution.FactMergingService;
import com.zhli.baymd.rag.core.memory.evolution.ProfileGenerationService;
import com.zhli.baymd.rag.dao.entity.UserEpisodeDO;
import com.zhli.baymd.rag.dao.entity.UserFactDO;
import com.zhli.baymd.rag.dao.mapper.UserEpisodeVectorMapper;
import com.zhli.baymd.rag.service.FollowUpTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 记忆提取编排器 — 异步编排 Fact 和 Episode 的提取、向量化、入库。
 *
 * <p>Phase 2 起在记忆提取完成后挂载主动随访规划（异步、失败不影响主流程）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryExtractionOrchestrator {

    private final FactExtractionService factService;
    private final EpisodeExtractionService episodeService;
    private final EmbeddingService embeddingService;
    private final UserEpisodeVectorMapper episodeVectorMapper;
    private final FactMergingService factMergingService;
    private final ProfileGenerationService profileService;
    private final FollowUpPlanService followUpPlanService;
    private final FollowUpTaskService followUpTaskService;

    /**
     * 异步提取记忆（不阻塞用户响应）。
     */
    public void extractAsync(String question, String answer,
                              String userId, String conversationId, String messageId) {
        CompletableFuture.runAsync(() -> {
            try {
                // 并行提取 Facts 和 Episode
                CompletableFuture<List<UserFactDO>> factsFuture =
                        CompletableFuture.supplyAsync(() ->
                                factService.extractAndSave(question, answer, userId, messageId));
                CompletableFuture<UserEpisodeDO> episodeFuture =
                        CompletableFuture.supplyAsync(() ->
                                episodeService.extractAndSave(question, answer, userId, conversationId));

                List<UserFactDO> facts = factsFuture.join();
                UserEpisodeDO episode = episodeFuture.join();

                // 向量化并写入
                embedFactsAsync(facts);
                embedEpisodeAsync(episode);

                // 触发进化：合并+画像
                factMergingService.mergeIfNeeded(userId);
                profileService.generateIfNeeded(userId);

                log.info("记忆提取完成: userId={}, facts={}, episode={}",
                        userId, facts.size(), episode != null ? 1 : 0);

                // 主动随访规划（Phase 2），失败不影响记忆提取结果
                planFollowUpAsync(question, answer, userId, conversationId);
            } catch (Exception e) {
                log.warn("记忆提取失败: userId={}", userId, e);
            }
        });
    }

    private void planFollowUpAsync(String question, String answer, String userId, String conversationId) {
        try {
            CompletableFuture.runAsync(() -> {
                try {
                    FollowUpPlan plan = followUpPlanService.plan(question, answer);
                    if (plan.shouldFollowUp()) {
                        followUpTaskService.createTask(userId, conversationId, plan);
                    }
                } catch (Exception e) {
                    log.warn("随访规划失败: userId={}", userId, e);
                }
            });
        } catch (Exception e) {
            log.warn("随访规划提交失败: userId={}", userId, e);
        }
    }

    private void embedFactsAsync(List<UserFactDO> facts) {
        if (facts.isEmpty()) return;
        CompletableFuture.runAsync(() -> {
            for (UserFactDO fact : facts) {
                try {
                    List<Float> vec = embeddingService.embed(fact.getFactText());
                    String vecStr = vec.stream().map(String::valueOf)
                            .collect(Collectors.joining(",", "[", "]"));
                    factService.embedFact(fact, vecStr);
                } catch (Exception e) {
                    log.warn("Fact 向量化失败: factId={}", fact.getId(), e);
                }
            }
        });
    }

    private void embedEpisodeAsync(UserEpisodeDO episode) {
        if (episode == null) return;
        CompletableFuture.runAsync(() -> {
            try {
                String text = episode.getTitle() + " " + episode.getSummary();
                List<Float> vec = embeddingService.embed(text);
                String vecStr = vec.stream().map(String::valueOf)
                        .collect(Collectors.joining(",", "[", "]"));
                episodeVectorMapper.insert(episode.getId(), vecStr);
            } catch (Exception e) {
                log.warn("Episode 向量化失败: epId={}", episode.getId(), e);
            }
        });
    }
}
