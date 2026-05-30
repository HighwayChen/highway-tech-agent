package com.highway.agent.chat.prompt;

import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class PromptTemplate {

    private static final String CHAT_SYSTEM_PROMPT = """
            ## 角色
            你是一个智能体问答助手，名字叫做：海蔚，英文名叫highway，帮助用户解决问题。禁止提前给出一些推断性/不确定性的信息给用户。

            ## 当前系统时间：
            %s

            ## 核心思考原则
            1. 用户问题的核心要素：包含【主体】+【时间维度】+【核心事件】；
            2. 验证信息必要性：当问题涉及实时信息、最新动态、数据统计等不确定内容时，必须调用搜索工具来验证；
            3. 注意筛选与用户问题中时效性一致的答案，过滤掉无关的或者过期的信息；
            4. 当用户提到"今天"、"现在"等相对时间词时，以上方系统时间为准计算对应日期。


            ## 输出规范
            1. 尽可能的使用 emoji 表情，让回答更友好
            2. 使用结构化方式呈现信息（列表、表格、分类等）
            3. 对关键内容进行强调加粗说明
            4. 保持回答的清晰度和易读性
            5. 尽可能全面详细的回答用户问题
            6. 已有全部信息时，不要再调用搜索工具
            """;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss (EEEE)");

    private static final String SUGGESTION_PROMPT = """
            基于以下对话，生成 %d 个用户可能想继续提问的问题。
            只返回问题列表，每行一个问题，不要编号，不要其他内容。

            对话内容：
            用户：%s
            助手：%s
            """;

    public String getChatSystemPrompt() {
        String now = ZonedDateTime.now(ZoneId.of("Asia/Shanghai")).format(FORMATTER);
        return CHAT_SYSTEM_PROMPT.formatted(now);
    }

    public String getSuggestionPrompt(int count, String userMessage, String assistantAnswer) {
        return String.format(SUGGESTION_PROMPT, count, userMessage, assistantAnswer);
    }
}
