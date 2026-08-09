package com.zhli.baymd.core.chunk.strategy;

import cn.hutool.core.util.IdUtil;
import com.zhli.baymd.core.chunk.ChunkingMode;
import com.zhli.baymd.core.chunk.ChunkingOptions;
import com.zhli.baymd.core.chunk.ChunkingStrategy;
import com.zhli.baymd.core.chunk.ParentChildOptions;
import com.zhli.baymd.core.chunk.VectorChunk;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 父子切块（Dify 风格）：大父块承载上下文，小子块精确检索。
 * <p>子块 metadata 携带 {@code parent_content}（父块全文），检索子块时附带父块上下文。
 * 分隔符提供时按分隔符切分打包，否则按固定长度切分。</p>
 */
@Component
public class ParentChildChunker implements ChunkingStrategy {

    @Override
    public ChunkingMode getType() {
        return ChunkingMode.PARENT_CHILD;
    }

    @Override
    public List<VectorChunk> chunk(String text, ChunkingOptions config) {
        ParentChildOptions opts = (ParentChildOptions) config;
        int parentMax = Math.max(1, opts.parentMaxChars());
        int childMax = Math.max(1, opts.childMaxChars());
        String sep = opts.separator() == null ? "" : opts.separator();

        List<String> parents = splitToChunks(text, parentMax, sep);
        List<VectorChunk> result = new ArrayList<>();
        int index = 0;

        for (String parent : parents) {
            for (String child : splitToChunks(parent, childMax, sep)) {
                Map<String, Object> meta = new HashMap<>();
                meta.put("chunk_type", "child");
                meta.put("parent_content", parent);
                result.add(VectorChunk.builder()
                        .chunkId(IdUtil.getSnowflakeNextIdStr())
                        .index(index++)
                        .content(child)
                        .metadata(meta)
                        .build());
            }
        }
        if (result.isEmpty() && !text.isBlank()) {
            result.add(VectorChunk.builder()
                    .chunkId(IdUtil.getSnowflakeNextIdStr())
                    .index(0)
                    .content(text.trim())
                    .build());
        }
        return result;
    }

    /** 按分隔符切分打包，或固定长度切分 */
    private List<String> splitToChunks(String text, int maxChars, String separator) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        if (!separator.isEmpty()) {
            String[] segs = text.split(Pattern.quote(separator), -1);
            StringBuilder sb = new StringBuilder();
            for (String seg : segs) {
                String s = seg.trim();
                if (s.isEmpty()) {
                    continue;
                }
                if (sb.length() > 0 && sb.length() + separator.length() + s.length() > maxChars) {
                    out.add(sb.toString());
                    sb.setLength(0);
                }
                if (sb.length() > 0) {
                    sb.append(separator);
                }
                sb.append(s);
            }
            if (sb.length() > 0) {
                out.add(sb.toString());
            }
            if (out.isEmpty()) {
                out.add(text.trim());
            }
        } else {
            for (int i = 0; i < text.length(); i += maxChars) {
                out.add(text.substring(i, Math.min(i + maxChars, text.length())));
            }
        }
        return out;
    }
}
