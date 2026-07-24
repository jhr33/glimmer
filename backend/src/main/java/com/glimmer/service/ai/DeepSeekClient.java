package com.glimmer.service.ai;

import com.glimmer.common.exception.BusinessException;
import com.glimmer.common.exception.ErrorCode;
import com.glimmer.config.ai.DeepSeekProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

/**
 * DeepSeek API 客户端封装
 * 见开发文档 §2.6.3 / §8.1
 * - 同步调用（stream=false）
 * - 调用失败时抛 BusinessException(INTERNAL_ERROR)
 */
@Slf4j
@Component
public class DeepSeekClient {

    private final RestTemplate restTemplate;
    private final DeepSeekProperties properties;
    private final ObjectMapper objectMapper;

    public DeepSeekClient(RestTemplate restTemplate, DeepSeekProperties properties) {
        this.restTemplate = restTemplate;
        this.properties = properties;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 调用 DeepSeek Chat Completion
     *
     * @param messages 消息列表（含 system 提示词）
     * @return AI 回复内容
     */
    public String chatCompletion(List<DeepSeekMessage> messages) {
        DeepSeekRequest request = new DeepSeekRequest();
        request.setModel(properties.getModel());
        request.setMessages(messages);
        request.setStream(false);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(properties.getApiKey());

        HttpEntity<DeepSeekRequest> entity = new HttpEntity<>(request, headers);

        try {
            DeepSeekResponse response = restTemplate.postForObject(
                    properties.getApiUrl(), entity, DeepSeekResponse.class);

            if (response == null || response.getChoices() == null || response.getChoices().isEmpty()) {
                log.error("DeepSeek 响应为空或无 choices");
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "AI 服务暂时不可用，请稍后重试");
            }

            String content = response.getChoices().get(0).getMessage().getContent();
            if (content == null || content.isBlank()) {
                log.warn("DeepSeek 返回空内容, usage={}", response.getUsage());
                content = "（AI 暂未返回内容）";
            }
            log.info("DeepSeek 调用成功: model={}, usage={}", properties.getModel(), response.getUsage());
            return content;
        } catch (BusinessException e) {
            throw e;
        } catch (HttpStatusCodeException e) {
            log.error("DeepSeek HTTP 错误: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "AI 服务暂时不可用，请稍后重试");
        } catch (RestClientException e) {
            log.error("DeepSeek 网络异常/超时", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "AI 服务暂时不可用，请稍后重试");
        } catch (Exception e) {
            log.error("DeepSeek 调用未知异常", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "AI 服务暂时不可用，请稍后重试");
        }
    }

    /**
     * 调用 DeepSeek Chat Completion（流式，回调方式）
     *
     * @param messages 消息列表（含 system 提示词）
     * @param callback 回调函数，每收到一个增量内容调用一次
     */
    public void chatCompletionStream(List<DeepSeekMessage> messages, StreamCallback callback) {
        DeepSeekRequest request = new DeepSeekRequest();
        request.setModel(properties.getModel());
        request.setMessages(messages);
        request.setStream(true);

        HttpURLConnection connection = null;
        BufferedReader reader = null;

        try {
            URL url = new URL(properties.getApiUrl());
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + properties.getApiKey());
            connection.setRequestProperty("Accept", "text/event-stream");
            connection.setDoOutput(true);
            connection.setDoInput(true);
            connection.setConnectTimeout(30000);
            connection.setReadTimeout(0); // 流式读取不超时

            // 发送请求体
            String jsonRequest = objectMapper.writeValueAsString(request);
            connection.getOutputStream().write(jsonRequest.getBytes("UTF-8"));
            connection.getOutputStream().flush();
            connection.getOutputStream().close();

            // 读取响应
            InputStream inputStream = connection.getInputStream();
            reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || "[DONE]".equals(line.trim())) {
                    continue;
                }
                // 去掉可能的 "data:" 前缀
                String data = line.trim();
                if (data.startsWith("data:")) {
                    data = data.substring(5).trim();
                }
                if (data.isEmpty() || "[DONE]".equals(data)) {
                    continue;
                }
                try {
                    JsonNode root = objectMapper.readTree(data);
                    JsonNode choices = root.get("choices");
                    if (choices != null && !choices.isEmpty()) {
                        JsonNode delta = choices.get(0).get("delta");
                        if (delta != null) {
                            JsonNode content = delta.get("content");
                            if (content != null && !content.isNull()) {
                                callback.onDelta(content.asText());
                            }
                        }
                        // 检查是否结束
                        JsonNode finishReason = choices.get(0).get("finish_reason");
                        if (finishReason != null && !finishReason.isNull()) {
                            break;
                        }
                    }
                } catch (Exception e) {
                    log.warn("解析 DeepSeek 流式响应失败: {}", data, e);
                }
            }

            log.info("DeepSeek 流式调用完成");

        } catch (Exception e) {
            log.error("DeepSeek 流式调用异常", e);
            callback.onError(e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception e) {
                    // ignore
                }
            }
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * 流式回调接口
     */
    @FunctionalInterface
    public interface StreamCallback {
        void onDelta(String delta);

        default void onError(Exception e) {
            // 默认空实现
        }
    }
}
