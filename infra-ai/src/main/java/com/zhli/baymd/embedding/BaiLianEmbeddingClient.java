package com.zhli.baymd.infra.embedding;

import com.zhli.baymd.infra.enums.ModelProvider;
import okhttp3.OkHttpClient;
import org.springframework.stereotype.Service;

@Service
public class BaiLianEmbeddingClient extends AbstractOpenAIStyleEmbeddingClient {

    public BaiLianEmbeddingClient(OkHttpClient syncHttpClient) {
        super(syncHttpClient);
    }

    @Override
    public String provider() {
        return ModelProvider.BAI_LIAN.getId();
    }

    @Override
    protected int maxBatchSize() {
        return 10; // MaaS 端点单次最大批量 10
    }
}
