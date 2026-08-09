package com.zhli.baymd.core.chunk.strategy;

import com.zhli.baymd.core.chunk.FixedSizeOptions;
import com.zhli.baymd.core.chunk.VectorChunk;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link FixedSizeTextChunker} 单测 — 固定大小 + 自定义分隔符切分。
 */
@DisplayName("固定大小分块器测试")
class FixedSizeTextChunkerTest {

    private final FixedSizeTextChunker chunker = new FixedSizeTextChunker();

    @Test
    @DisplayName("自定义分隔符：小段落按分隔符切分为独立块")
    void separator_splitsBySeparator() {
        List<VectorChunk> chunks = chunker.chunk(
                "AAA\n\nBBB\n\nCCC",
                new FixedSizeOptions(6, 0, "\n\n"));

        assertThat(chunks).hasSize(3);
        assertThat(chunks.get(0).getContent()).isEqualTo("AAA");
        assertThat(chunks.get(1).getContent()).isEqualTo("BBB");
        assertThat(chunks.get(2).getContent()).isEqualTo("CCC");
    }

    @Test
    @DisplayName("自定义分隔符：足够大的块长时小段落被合并打包")
    void separator_packsSmallSegments() {
        List<VectorChunk> chunks = chunker.chunk(
                "AAA\n\nBBB\n\nCCC",
                new FixedSizeOptions(100, 0, "\n\n"));

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).getContent()).isEqualTo("AAA\n\nBBB\n\nCCC");
    }

    @Test
    @DisplayName("自定义分隔符：超长单段落保持完整不截断")
    void separator_keepsLongSegmentWhole() {
        String longSeg = "X".repeat(500);
        List<VectorChunk> chunks = chunker.chunk(
                longSeg + "\n\nBBB",
                new FixedSizeOptions(100, 0, "\n\n"));

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).getContent()).isEqualTo(longSeg);
        assertThat(chunks.get(1).getContent()).isEqualTo("BBB");
    }

    @Test
    @DisplayName("无分隔符：固定大小切分，每块 ≤ chunkSize")
    void noSeparator_fixedSize() {
        List<VectorChunk> chunks = chunker.chunk(
                "ABCDEFGHIJKLMNOPQRSTUVWXYZ",
                new FixedSizeOptions(5, 0, null));

        assertThat(chunks).isNotEmpty();
        assertThat(chunks.size()).isGreaterThan(1);
        for (VectorChunk c : chunks) {
            assertThat(c.getContent().length()).isLessThanOrEqualTo(5);
        }
        // 所有块拼接起来应包含原文（无重叠时）
        String joined = chunks.stream().map(VectorChunk::getContent).collect(Collectors.joining());
        assertThat(joined).isEqualTo("ABCDEFGHIJKLMNOPQRSTUVWXYZ");
    }

    @Test
    @DisplayName("无分隔符：重叠生效")
    void noSeparator_withOverlap() {
        List<VectorChunk> chunks = chunker.chunk(
                "ABCDEFGHIJ",
                new FixedSizeOptions(5, 2, null));

        assertThat(chunks).hasSize(3);
        // 相邻块有重叠
        assertThat(chunks.get(0).getContent()).isEqualTo("ABCDE");
        assertThat(chunks.get(1).getContent()).startsWith("DE");
    }
}
