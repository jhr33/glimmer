package com.glimmer.common.util;

import com.glimmer.common.exception.BusinessException;
import com.glimmer.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 违禁词过滤服务
 * <p>
 * 对用户提交的文本内容进行违禁词检测，支持：
 * - 大小写不敏感
 * - 全角/半角归一化
 * - 重复字符压缩（如"他妈的" → "他的"）
 * - 空白字符忽略
 * </p>
 */
@Slf4j
@Component
public class BannedWordFilterService {

    /** 违禁词列表（正则表达式，按严重程度排序） */
    private final List<Pattern> bannedPatterns;

    public BannedWordFilterService() {
        this.bannedPatterns = buildBannedPatterns();
    }

    /**
     * 检测文本是否包含违禁词
     *
     * @param content 待检测文本
     * @param context 业务场景描述（用于日志）
     * @throws BusinessException 若包含违禁词则抛出 CONTENT_BANNED
     */
    public void check(String content, String context) {
        if (content == null || content.isBlank()) {
            return;
        }

        String normalized = normalize(content);

        for (Pattern pattern : bannedPatterns) {
            if (pattern.matcher(normalized).find()) {
                log.warn("违禁词检测拦截: context={}, pattern={}, content={}", context, pattern.pattern(), truncate(content));
                throw new BusinessException(ErrorCode.CONTENT_BANNED);
            }
        }
    }

    /**
     * 检测文本是否包含违禁词（静默模式，仅返回结果不抛异常）
     */
    public boolean containsBannedWord(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        String normalized = normalize(content);
        for (Pattern pattern : bannedPatterns) {
            if (pattern.matcher(normalized).find()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 归一化：去除空白、压缩重复字符、统一小写
     */
    private String normalize(String text) {
        // 1. 去除所有空白字符
        String result = text.replaceAll("\\s+", "");
        // 2. 压缩连续重复字符（如"哈哈哈" → "哈"，"真的的" → "真的"）
        result = result.replaceAll("(.)\\1{2,}", "$1");
        // 3. 统一小写
        result = result.toLowerCase();
        // 4. 全角转半角（基础常见符号）
        result = toHalfWidth(result);
        return result;
    }

    /**
     * 全角数字/字母转半角
     */
    private String toHalfWidth(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 0xFF01 && c <= 0xFF5E) {
                sb.append((char) (c - 0xFEE0));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private String truncate(String s) {
        return s.length() > 100 ? s.substring(0, 100) + "..." : s;
    }

    /**
     * 构建违禁词正则列表
     * <p>
     * 注意：此处仅存放模式定义，具体词汇已做脱敏处理，不在日志/异常中暴露
     * </p>
     */
    private List<Pattern> buildBannedPatterns() {
        String[] patterns = {
            // 粗口/辱骂类
            "(?i)(操|草|肏|fuck|f.u.c.k)",
            "(?i)(妈的|妈逼|妈个逼|他妈的|去你妈的|去你妈|干你|操你)",
            "(?i)(废物|垃圾|人渣|狗东西|不是东西|不是人)",
            "(?i)(王八蛋|王八|婊子|贱货|贱人|bitch|slut)",
            "(?i)(畜生|禽兽|牲口|狗日的|日你|日死)",
            // 人身攻击类
            "(?i)(白痴|脑残|智障|傻逼|傻b|sb|白痴|弱智|废物点心)",
            "(?i)(不要脸|无耻|下流|下贱|恶心人|龌龊)",
            "(?i)(滚[蛋]?|去死|找死|欠揍|找打|讨打)",
            // 性别歧视类
            "(?i)(渣男|渣女|渣男|渣女)",
            "(?i)(绿茶[婊b]?|白莲花|心机婊|假惺惺)",
            // 暴力威胁类
            "(?i)(弄死|打死|捅[死你]?|砍[死你]?|杀[死你]?|血洗|血虐)",
            "(?i)(约架|干架|砍人|打人|教训你|收拾你)",
            // 涉政敏感（基础）
            "(?i)(敏感词占位a|敏感词占位b)",
            // 常见网络黑话
            "(?i)(小黑[子孩]?|黑子|喷子|杠精|键盘侠)",
            "(?i)(举报狗|打小报告|告密|出卖)",
        };

        return Arrays.stream(patterns)
                .map(Pattern::compile)
                .collect(Collectors.toList());
    }
}
