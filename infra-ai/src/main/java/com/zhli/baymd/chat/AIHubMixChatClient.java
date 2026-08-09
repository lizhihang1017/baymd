

package com.zhli.baymd.infra.chat;

import com.zhli.baymd.framework.convention.ChatRequest;
import com.zhli.baymd.framework.trace.RagTraceNode;
import com.zhli.baymd.infra.enums.ModelProvider;
import com.zhli.baymd.infra.model.ModelTarget;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AIHubMixChatClient extends AbstractOpenAIStyleChatClient {

    @Override
    public String provider() {
        return ModelProvider.AI_HUB_MIX.getId();
    }

    @Override
    @RagTraceNode(name = "aihubmix-chat", type = "LLM_PROVIDER")
    public String chat(ChatRequest request, ModelTarget target) {
        return doChat(request, target);
    }

    @Override
    @com.zhli.baymd.framework.trace.RagTraceNode(name = "AIHubMixChatClient-stream", type = "LLM_PROVIDER")
    public StreamCancellationHandle streamChat(ChatRequest request, StreamCallback callback, ModelTarget target) {
        return doStreamChat(request, callback, target);
    }
}
