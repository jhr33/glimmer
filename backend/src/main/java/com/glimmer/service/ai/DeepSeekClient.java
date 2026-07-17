package com.glimmer.service.ai;

import com.glimmer.common.exception.BusinessException;
import com.glimmer.common.exception.ErrorCode;
import com.glimmer.config.ai.DeepSeekProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

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

    public DeepSeekClient(RestTemplate restTemplate, DeepSeekProperties properties) {
        this.restTemplate = restTemplate;
        this.properties = properties;
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
}
