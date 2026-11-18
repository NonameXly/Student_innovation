package com.innovation.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.innovation.common.R;
import com.innovation.entity.AiChat;
import com.innovation.entity.AiMessage;
import com.innovation.mapper.AiChatMapper;
import com.innovation.mapper.AiMessageMapper;
import com.innovation.security.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.RequestBody;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiChatMapper chatMapper;
    private final AiMessageMapper messageMapper;
    private final SecurityUtil securityUtil;
    private final OkHttpClient okHttpClient;
    private final ObjectMapper objectMapper;

    @Value("${app.ai.url}")
    private String aiUrl;
    @Value("${app.ai.api-key}")
    private String aiApiKey;
    @Value("${app.ai.model}")
    private String aiModel;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @GetMapping("/chat-list")
    public R<?> chatList(@RequestParam(defaultValue = "1") Integer page,
                         @RequestParam(defaultValue = "20") Integer size) {
        Long userId = securityUtil.getCurrentUserId();
        var list = chatMapper.selectList(new LambdaQueryWrapper<AiChat>()
                .eq(AiChat::getUserId, userId)
                .orderByDesc(AiChat::getUpdatedAt));
        List<Map<String, Object>> result = new ArrayList<>();
        for (AiChat c : list) {
            result.add(Map.of(
                    "id", c.getId(),
                    "title", c.getTitle(),
                    "time", c.getUpdatedAt() != null ? c.getUpdatedAt().format(FMT) : ""
            ));
        }
        return R.ok(Map.of("list", result));
    }

    @GetMapping("/chat/{chatId}/messages")
    public R<?> chatMessages(@PathVariable Long chatId) {
        Long userId = securityUtil.getCurrentUserId();
        AiChat chat = chatMapper.selectById(chatId);
        if (chat == null || !userId.equals(chat.getUserId())) {
            return R.fail("对话不存在或无权访问");
        }
        List<AiMessage> msgs = messageMapper.selectList(new LambdaQueryWrapper<AiMessage>()
                .eq(AiMessage::getChatId, chatId)
                .orderByAsc(AiMessage::getId));
        List<Map<String, Object>> list = new ArrayList<>();
        for (AiMessage m : msgs) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", m.getId());
            row.put("role", m.getRole());
            row.put("content", m.getContent() != null ? m.getContent() : "");
            row.put("time", m.getCreatedAt() != null ? m.getCreatedAt().format(FMT) : "");
            list.add(row);
        }
        return R.ok(Map.of("list", list));
    }

    @PostMapping("/chat")
    public R<?> createChat(@org.springframework.web.bind.annotation.RequestBody Map<String, String> body) {
        Long userId = securityUtil.getCurrentUserId();
        AiChat chat = new AiChat();
        chat.setUserId(userId);
        chat.setTitle(body.getOrDefault("title", "新对话"));
        chatMapper.insert(chat);
        return R.ok(Map.of(
                "id", chat.getId(),
                "title", chat.getTitle(),
                "time", chat.getCreatedAt() != null ? chat.getCreatedAt().format(FMT) : ""
        ));
    }

    @PostMapping("/chat/message")
    public R<?> sendMessage(@org.springframework.web.bind.annotation.RequestBody Map<String, Object> body) {
        Long userId = securityUtil.getCurrentUserId();
        Long chatId = body.get("chatId") != null ? Long.parseLong(body.get("chatId").toString()) : null;
        String userMessage = (String) body.get("message");

        // 校验 chatId 归属权
        if (chatId != null) {
            AiChat existingChat = chatMapper.selectById(chatId);
            if (existingChat == null || !userId.equals(existingChat.getUserId())) {
                return R.fail("对话不存在或无权访问");
            }
        }

        // 如果没有chatId，创建一个新对话
        if (chatId == null) {
            AiChat chat = new AiChat();
            chat.setUserId(userId);
            chat.setTitle(userMessage.length() > 20 ? userMessage.substring(0, 20) + "..." : userMessage);
            chatMapper.insert(chat);
            chatId = chat.getId();
        }

        // 保存用户消息
        AiMessage userMsg = new AiMessage();
        userMsg.setChatId(chatId);
        userMsg.setRole("user");
        userMsg.setContent(userMessage);
        messageMapper.insert(userMsg);

        // 调用 Dify API
        String aiResponse;
        try {
            aiResponse = callAiApi(userMessage, userId.toString());
        } catch (Exception e) {
            log.error("AI API 调用失败", e);
            String msg = e.getMessage();
            if (msg != null && msg.startsWith("抱歉，")) {
                aiResponse = msg;
            } else {
                aiResponse = "抱歉，AI 服务暂时不可用，请稍后再试。";
            }
        }

        // 保存 AI 回复
        AiMessage aiMsg = new AiMessage();
        aiMsg.setChatId(chatId);
        aiMsg.setRole("assistant");
        aiMsg.setContent(aiResponse);
        messageMapper.insert(aiMsg);

        return R.ok(Map.of(
                "id", aiMsg.getId(),
                "response", aiResponse,
                "time", aiMsg.getCreatedAt() != null ? aiMsg.getCreatedAt().format(FMT) : ""
        ));
    }

    private String callAiApi(String message, String userId) throws IOException {
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", aiModel);
        requestBody.put("messages", List.of(
                Map.of("role", "system", "content", "你是一个高校学生创新竞赛平台的AI助手，帮助学生解答竞赛相关问题。"),
                Map.of("role", "user", "content", message)
        ));

        String json = objectMapper.writeValueAsString(requestBody);

        Request request = new Request.Builder()
                .url(aiUrl)
                .addHeader("Authorization", "Bearer " + aiApiKey)
                .addHeader("Content-Type", "application/json")
                .post(okhttp3.RequestBody.create(json, MediaType.parse("application/json")))
                .build();

        try (Response response = okHttpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                log.error("AI API 返回错误, code={}, body={}", response.code(), responseBody);
                throw new IOException(toUserFacingAiError(response.code(), responseBody));
            }
            JsonNode node = objectMapper.readTree(responseBody);
            JsonNode choices = node.get("choices");
            if (choices != null && choices.isArray() && !choices.isEmpty()) {
                return choices.get(0).get("message").get("content").asText();
            }
            return "AI 返回了空回复";
        }
    }

    /** 将上游错误转为可展示提示，避免泄露完整响应 */
    private String toUserFacingAiError(int httpCode, String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode err = root.get("error");
            if (err != null) {
                String code = err.has("code") ? err.get("code").asText("") : "";
                String message = err.has("message") ? err.get("message").asText("") : "";
                if ("AllocationQuota.FreeTierOnly".equals(code)
                        || message.toLowerCase().contains("free quota")
                        || message.contains("免费")) {
                    return "抱歉，AI 服务免费额度已用尽，请在阿里云百炼控制台开通付费或更换有效 API Key 后重试。";
                }
                if (httpCode == 401 || "InvalidApiKey".equalsIgnoreCase(code)) {
                    return "抱歉，AI 服务 API Key 无效或已过期，请检查 application.yml 中的 app.ai.api-key 配置。";
                }
                if (!message.isBlank()) {
                    return "抱歉，AI 服务调用失败：" + message;
                }
            }
        } catch (Exception ignored) {
            // fall through
        }
        return "抱歉，AI 服务暂时不可用（HTTP " + httpCode + "），请稍后再试。";
    }
}
