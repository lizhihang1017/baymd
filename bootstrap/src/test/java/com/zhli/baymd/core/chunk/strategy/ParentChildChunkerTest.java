package com.zhli.baymd.core.chunk.strategy;

import com.zhli.baymd.core.chunk.ParentChildOptions;
import com.zhli.baymd.core.chunk.VectorChunk;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ParentChildChunker} 单测 — 父块上下文 + 子块检索。
 */
@DisplayName("父子切块测试")
class ParentChildChunkerTest {

    private final ParentChildChunker chunker = new ParentChildChunker();

    @Test
    @DisplayName("按分隔符切分：每个子块携带父块上下文")
    void childCarriesParentContent() {
        String text = "AAA\n\nBBB\n\nCCC";
        List<VectorChunk> chunks = chunker.chunk(text, new ParentChildOptions(100, 10, "\n\n"));

        assertThat(chunks).isNotEmpty();
        for (VectorChunk c : chunks) {
            assertThat(c.getContent()).isNotBlank();
            // 子块内容 ≤ childMaxChars
            assertThat(c.getContent().length()).isLessThanOrEqualTo(100);
            // 携带父块上下文
            assertThat(c.getMetadata()).containsKey("parent_content");
            assertThat((String) c.getMetadata().get("parent_content")).contains(text);
        }
    }

    @Test
    @DisplayName("无分隔符：固定长度切分父块与子块")
    void fixedLength() {
        String text = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        List<VectorChunk> chunks = chunker.chunk(text, new ParentChildOptions(20, 8, null));

        assertThat(chunks).isNotEmpty();
        for (VectorChunk c : chunks) {
            assertThat(c.getContent().length()).isLessThanOrEqualTo(20);
            assertThat(c.getMetadata()).containsKey("parent_content");
        }
    }
}
