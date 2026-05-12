package com.highway.agent.research.prompt;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class DeepResearchPrompt {

    public String questionAnalyzerPrompt(String userQuestion, String previousClarification) {
        String context = previousClarification == null || previousClarification.isBlank()
                ? ""
                : "\n用户已补充说明：" + previousClarification + "\n（注意：用户已补充过信息，应倾向于判定为 clear，除非仍存在严重模糊）";

        return """
                你是一个研究问题分析专家。请分析以下用户问题，判断其清晰度。

                用户问题：%s
                %s

                请按以下 JSON 格式返回分析结果（不要包含其他内容）：
                {
                  "clarity_level": "clear" 或 "needs_clarification",
                  "ambiguous_points": ["模糊点1", "模糊点2"],
                  "clarification_questions": ["澄清问题1", "澄清问题2"],
                  "interpreted_intent": "对用户意图的理解"
                }

                判断标准：
                - "clear"：问题主体明确、范围清晰、意图具体（如"DeepSeek V3和GPT-4o的技术架构对比"）
                - "needs_clarification"：主体模糊、范围过大、或需要进一步缩小方向（如"聊聊AI"）

                clarification_questions 最多 5 个，只在 clarity_level 为 needs_clarification 时提供。
                尽量克制提问，只问最关键的模糊点，不要过度追问。
                """.formatted(userQuestion, context);
    }

    public String plannerPrompt(String question, int maxSubQuestions, String critiqueFeedback) {
        String critiqueContext = critiqueFeedback == null || critiqueFeedback.isBlank()
                ? ""
                : "\n\n上一轮研究的反馈（请据此调整计划）：\n" + critiqueFeedback;

        return """
                你是一个研究规划专家。请将以下研究问题分解为最多 %d 个子问题，并为每个子问题设计搜索策略。

                研究问题：%s
                %s

                请按以下 JSON 格式返回研究计划（不要包含其他内容）：
                {
                  "summary": "研究计划的简要描述",
                  "subQuestions": [
                    {
                      "index": 1,
                      "question": "子问题1",
                      "searchStrategy": "broad 或 targeted 或 deep",
                      "searchQueries": ["搜索关键词1", "搜索关键词2"]
                    }
                  ],
                  "estimatedSearchCount": 8
                }

                规划原则：
                1. 子问题应覆盖原始问题的不同维度，避免重复
                2. searchStrategy 说明：
                   - broad：广泛搜索，适用于概览性子问题
                   - targeted：精准搜索，适用于具体事实性子问题
                   - deep：深度搜索，需要多轮迭代挖掘
                3. searchQueries 应使用中英文混合的关键词，每个子问题 2-3 个查询
                4. estimatedSearchCount 为预估总搜索次数
                """.formatted(maxSubQuestions, question, critiqueContext);
    }

    public String criticPrompt(String originalQuestion, String researchPlanSummary,
                               String extractedContentsSummary, int iterationCount, int maxIterations) {
        return """
                你是一个研究质量评估专家。请评估当前收集的信息是否足以回答原始研究问题。

                原始研究问题：%s

                研究计划：%s

                已收集的信息摘要：
                %s

                当前迭代轮次：%d / %d

                请按以下 JSON 格式返回评估结果（不要包含其他内容）：
                {
                  "sufficient": true 或 false,
                  "completenessScore": 0.0 到 1.0 之间的分数,
                  "gaps": ["信息缺口1", "信息缺口2"],
                  "revisionSuggestion": "修订建议，说明还需要补充哪些方面的信息"
                }

                评估标准：
                - sufficient 为 true：已收集的信息能够充分回答原始问题
                - sufficient 为 false：仍有明显的信息缺口需要补充
                - completenessScore：0.7 以上视为基本充分
                - gaps：列出仍需补充的具体信息方向
                - 注意：当前已是第 %d 轮，最多 %d 轮，请据此权衡是否继续
                """.formatted(originalQuestion, researchPlanSummary, extractedContentsSummary,
                iterationCount, maxIterations, iterationCount, maxIterations);
    }

    public String reporterPrompt(String originalQuestion, String researchPlanSummary,
                                  String extractedContents, String references) {
        return """
                你是一个深度研究报告撰写专家。请基于以下收集的信息，撰写一份结构化的深度研究报告。

                原始研究问题：%s

                研究计划：%s

                收集的信息：
                %s

                参考来源：
                %s

                报告要求：
                1. 开头用一段话总结核心发现
                2. 按主题分章节，每个章节用 ## 标题
                3. 在具体数据或观点后标注来源编号，格式为 [1]、[2] 等
                4. 最后列出所有参考来源
                5. 使用 emoji 增强可读性
                6. 对关键内容加粗强调
                7. 使用表格或列表呈现对比信息
                8. 语言风格专业但易于理解
                """.formatted(originalQuestion, researchPlanSummary, extractedContents, references);
    }

    public String extractContentPrompt(String pageContent, String focusTopic) {
        return """
                请从以下网页内容中提取与「%s」相关的关键信息。

                网页内容：
                %s

                要求：
                1. 只提取与主题直接相关的事实、数据和观点
                2. 去除广告、导航栏等无关内容
                3. 保留具体的数据和细节
                4. 用简洁的要点形式呈现
                5. 如果内容与主题无关，返回空字符串
                """.formatted(focusTopic, pageContent);
    }

    public String visualReporterPrompt(String originalQuestion, String researchPlanSummary,
                                        String extractedContents, String references) {
        return """
                你是一个数据可视化报告生成专家。请基于以下研究信息，生成一份结构化的可视化报告数据（JSON 格式）。

                原始研究问题：%s

                研究计划：%s

                收集的信息：
                %s

                参考来源：
                %s

                请严格按照以下 JSON 格式输出（不要包含 markdown 代码块标记，不要包含其他内容）：
                {
                  "title": "报告标题",
                  "summary": "2-3句话的核心摘要",
                  "keyFindings": [
                    {"icon": "💡", "title": "发现标题", "content": "1-2句话的发现描述"}
                  ],
                  "statistics": [
                    {"label": "指标名称", "value": "带单位的值如 23.5%%", "trend": "up 或 down 或 stable", "description": "趋势说明"}
                  ],
                  "charts": [
                    {
                      "id": "chart_1",
                      "title": "图表标题",
                      "chartType": "bar 或 pie 或 line 或 radar",
                      "option": { 合法的 ECharts option JSON 对象 }
                    }
                  ],
                  "conclusions": ["结论1", "结论2"]
                }

                生成规则：
                1. keyFindings: 3-6 条核心发现，icon 使用 emoji（💡⚡🔍📈🎯🚀等）
                2. statistics: 3-5 个关键数字指标，value 必须带单位（如 "1.2万亿"、"67%%"），trend 表示趋势方向
                3. charts: 最多 4 个图表，每个图表的 option 必须是合法的 ECharts option JSON
                   - bar: 适合对比类数据（如各厂商市场份额、功能对比）
                   - pie: 适合占比类数据（如市场占比、资源分配）
                   - line: 适合趋势类数据（如增长趋势、时间序列）
                   - radar: 适合多维度对比（如技术能力雷达图）
                   - option 中不要设置 color 数组（前端统一主题色）
                   - 每个 chart 的 id 必须以 "chart_" 开头且唯一
                   - option 中使用中文标签
                4. conclusions: 3-5 条结论性总结
                5. 所有数据必须基于收集的信息，不要编造数据
                """.formatted(originalQuestion, researchPlanSummary, extractedContents, references);
    }

    public String reformulateQueryPrompt(String originalQuery, String gapDescription) {
        return """
                基于以下信息缺口，请重构搜索查询以获取更精准的结果。

                原始查询：%s
                信息缺口：%s

                要求：
                1. 返回 1-2 个重构后的搜索查询，每行一个
                2. 查询应针对信息缺口设计，使用更具体的关键词
                3. 中英文混合，包含专业术语
                4. 只返回查询文本，不要其他内容
                """.formatted(originalQuery, gapDescription);
    }
}
