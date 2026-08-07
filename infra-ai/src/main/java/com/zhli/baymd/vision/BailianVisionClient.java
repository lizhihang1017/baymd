package com.zhli.baymd.infra.vision;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.zhli.baymd.infra.config.AIModelProperties;
import com.zhli.baymd.infra.enums.ModelCapability;
import com.zhli.baymd.infra.http.HttpMediaTypes;
import com.zhli.baymd.infra.http.HttpResponseHelper;
import com.zhli.baymd.infra.http.ModelClientErrorType;
import com.zhli.baymd.infra.http.ModelClientException;
import com.zhli.baymd.infra.http.ModelUrlResolver;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 百炼视觉客户端 — 走百炼 OpenAI 兼容 /chat/completions 接口，
 * content 数组携带 image_url（base64 data URL）实现多模态解析。
 *
 * <p>复用 {@code ai.providers.bailian} 的 url/api-key 配置与 {@code endpoints.chat}。
 * 仅当 {@code ai.vision.default-model} 配置时启用。</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "ai.vision", name = "default-model")
public class BailianVisionClient implements VisionService {

    private static final String BAILIAN = "bailian";

    private final OkHttpClient httpClient;
    private final AIModelProperties properties;

    public BailianVisionClient(OkHttpClient httpClient, AIModelProperties properties) {
        this.httpClient = httpClient;
        this.properties = properties;
    }

    @Override
    public String extractText(byte[] imageBytes, String mimeType, String prompt) {
        Objects.requireNonNull(imageBytes, "imageBytes");
        if (imageBytes.length == 0) {
            throw new ModelClientException("vision 图片为空", ModelClientErrorType.INVALID_RESPONSE, null);
        }
        if (mimeType == null || mimeType.isBlank()) {
            mimeType = "image/png";
        }
        if (prompt == null || prompt.isBlank()) {
            prompt = "请提取这张图片中的全部文字信息，保持原始结构与数值准确。";
        }

        AIModelProperties.ModelCandidate candidate = resolveVisionCandidate();
        AIModelProperties.ProviderConfig provider = properties.getProviders().get(candidate.getProvider());
        if (provider == null) {
            throw new ModelClientException("vision 提供商未配置: " + candidate.getProvider(),
                    ModelClientErrorType.INVALID_RESPONSE, null);
        }
        if (provider.getApiKey() == null || provider.getApiKey().isBlank()) {
            throw new ModelClientException("vision 提供商缺少 api-key", ModelClientErrorType.UNAUTHORIZED, null);
        }
        String url = ModelUrlResolver.resolveUrl(provider, candidate, ModelCapability.CHAT);

        String dataUrl = "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(imageBytes);

        // 构建 OpenAI 多模态消息体
        JsonObject body = new JsonObject();
        body.addProperty("model", candidate.getModel());

        JsonArray contentArr = new JsonArray();
        JsonObject textPart = new JsonObject();
        textPart.addProperty("type", "text");
        textPart.addProperty("text", prompt);
        contentArr.add(textPart);

        JsonObject imagePart = new JsonObject();
        imagePart.addProperty("type", "image_url");
        JsonObject imgUrl = new JsonObject();
        imgUrl.addProperty("url", dataUrl);
        imagePart.add("image_url", imgUrl);
        contentArr.add(imagePart);

        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.add("content", contentArr);

        JsonArray messages = new JsonArray();
        messages.add(userMsg);
        body.add("messages", messages);

        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(body.toString(), HttpMediaTypes.JSON))
                .addHeader("Authorization", "Bearer " + provider.getApiKey())
                .build();

        log.info("BailianVisionClient 调用: model={}, imageBytes={}, mimeType={}",
                candidate.getModel(), imageBytes.length, mimeType);

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errBody = HttpResponseHelper.readBody(response.body());
                log.warn("vision 请求失败: status={}, body={}", response.code(), errBody);
                throw new ModelClientException("vision 请求失败: HTTP " + response.code(),
                        ModelClientErrorType.fromHttpStatus(response.code()), response.code());
            }
            JsonObject json = HttpResponseHelper.parseJson(response.body(), BAILIAN);
            if (json.has("error")) {
                throw new ModelClientException("vision 错误: " + json.get("error"),
                        ModelClientErrorType.PROVIDER_ERROR, null);
            }
            return extractContent(json);
        } catch (IOException e) {
            throw new ModelClientException("vision 请求异常: " + e.getMessage(),
                    ModelClientErrorType.NETWORK_ERROR, null, e);
        }
    }

    private String extractContent(JsonObject json) {
        if (!json.has("choices")) {
            throw new ModelClientException("vision 响应缺少 choices",
                    ModelClientErrorType.INVALID_RESPONSE, null);
        }
        JsonObject choice = json.getAsJsonArray("choices").get(0).getAsJsonObject();
        if (!choice.has("message")) {
            throw new ModelClientException("vision 响应缺少 message",
                    ModelClientErrorType.INVALID_RESPONSE, null);
        }
        JsonObject message = choice.getAsJsonObject("message");
        return message.has("content") ? message.get("content").getAsString() : "";
    }

    private AIModelProperties.ModelCandidate resolveVisionCandidate() {
        AIModelProperties.ModelGroup group = properties.getVision();
        List<AIModelProperties.ModelCandidate> candidates = group.getCandidates();
        if (candidates == null || candidates.isEmpty()) {
            throw new ModelClientException("未配置 ai.vision 候选模型",
                    ModelClientErrorType.INVALID_RESPONSE, null);
        }
        String defaultModel = group.getDefaultModel();
        if (defaultModel != null && !defaultModel.isBlank()) {
            for (AIModelProperties.ModelCandidate c : candidates) {
                if (defaultModel.equals(c.getId()) || defaultModel.equals(c.getModel())) {
                    return c;
                }
            }
        }
        // 取第一个启用的候选
        for (AIModelProperties.ModelCandidate c : candidates) {
            if (c.getEnabled() == null || c.getEnabled()) {
                return c;
            }
        }
        return candidates.get(0);
    }
}
