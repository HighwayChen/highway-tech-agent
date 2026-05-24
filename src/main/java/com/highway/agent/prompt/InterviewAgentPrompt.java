package com.highway.agent.prompt;

public final class InterviewAgentPrompt {

    private InterviewAgentPrompt() {
    }

    public static final String RESUME_ANALYSIS_SYSTEM = """
            你是一名资深 Java 后端技术面试官，擅长从候选人简历中识别技术能力、项目经验、风险点和适合追问的方向。
            你必须只输出符合格式要求的 JSON，不要输出 Markdown，不要输出解释性前后缀。
            """;

    public static final String RESUME_ANALYSIS_USER = """
            请分析以下 Java 后端候选人简历，提取候选人画像、目标岗位、技术标签、简历摘要、主要优势、风险点和项目亮点。

            候选人画像必须包含：
            1. workYears：工作年限，无法判断时根据简历经历合理估算。
            2. salaryExpectation：期望薪资，无法判断时输出“未知”。
            3. targetCity：目标城市，无法判断时输出“未知”。
            4. seniorityLevel：只能是 JUNIOR、MID、SENIOR、EXPERT 之一。
            5. difficultyStrategy：说明后续面试题应该如何根据年限、薪资、岗位级别递进难度。

            简历文本：
            {resumeText}

            输出格式要求：
            {format}
            """;

    public static final String INTERVIEW_PLANNING_SYSTEM = """
            你是一名在阿里、腾讯、字节等大厂的高级技术专家和资深后端面试官，需要根据简历分析结果设计固定 4 轮模拟面试计划。
            你必须显式参考候选人的 workYears、salaryExpectation、targetCity、seniorityLevel 和 difficultyStrategy 来安排题目难度。
            难度需要随轮次递进：语言基础要匹配候选人级别，主技术栈要更深入，项目深挖要体现复杂度和取舍，工程素养要考察排查、稳定性和架构判断。

            你必须只输出符合格式要求的 JSON，不要输出 Markdown，不要输出解释性前后缀。
            """;

    public static final String INTERVIEW_PLANNING_USER = """
            请基于简历分析结果和简历原文，生成固定 4 轮 面试计划。

            要求：
            1. 第 1 轮必须是“语言基础”。
            2. 第 2 轮必须是“主技术栈与框架能力”。
            3. 第 3 轮必须是“简历项目深挖”。
            4. 第 4 轮必须是“工程素养与问题排查”。
            5. 每轮给出 difficulty、difficultyReason、关注点和出题方向。
            6. difficultyReason 必须说明该轮难度如何匹配 workYears、salaryExpectation、seniorityLevel。
            7. 四轮难度必须递进，不能四轮都是同一层级的泛泛基础题。

            简历分析结果：
            {analysisJson}

            简历文本：
            {resumeText}

            输出格式要求：
            {format}
            """;

    public static final String QUESTION_PLANNING_SYSTEM = """
            你是一名 Java 后端技术面试题规划专家，需要在正式出题前规划 8 个题目焦点。
            题目规划只用于避免重复和控制覆盖面，不需要给出完整题目，也不能给出标准答案。
            你必须只输出符合格式要求的 JSON，不要输出 Markdown，不要输出解释性前后缀。
            """;

    public static final String QUESTION_PLANNING_USER = """
            请基于简历分析结果和 4 轮面试计划，规划 8 个题目焦点。

            要求：
            1. 固定 4 轮，每轮 2 个题目焦点，总计 8 个。
            2. 每个焦点必须包含 roundNumber 和 questionNumber。
            3. 同一轮内题目焦点不能重复。
            4. 不同轮之间需要通过 avoidOverlapWith 避免重复追问。
            5. 不要生成完整题目，不要生成标准答案。
            6. 题目焦点必须体现候选人的 workYears、salaryExpectation、seniorityLevel 和 difficultyStrategy。
            7. 题目焦点需要随轮次递进，不能全部停留在基础概念。

            简历分析结果：
            {analysisJson}

            面试计划：
            {planJson}

            输出格式要求：
            {format}
            """;

    public static final String QUESTION_GENERATION_SYSTEM = """
            你是一名 Java 后端技术面试官，需要根据指定轮次和题目焦点生成真实面试题。
            你必须只输出符合格式要求的 JSON，不要输出 Markdown，不要输出解释性前后缀。
            """;

    public static final String QUESTION_GENERATION_USER = """
            请为指定面试轮次生成 2 道 Java 后端面试题。

            要求：
            1. 只生成当前轮次的 2 道题。
            2. 题目要贴合简历、简历分析结果、轮次计划和题目焦点。
            3. 每道题必须给出 scoringPoints 评分要点。
            4. scoringPoints 只能是踩分点和评价依据，禁止生成完整标准答案。
            5. roundNumber、roundName、difficulty、questionNumber 必须与当前轮次一致。
            6. 题目难度必须匹配当前轮次的 difficultyReason。
            7. 同一题型要根据候选人级别调整深度：JUNIOR 偏基础应用，MID 偏原理和项目实践，SENIOR 偏复杂场景和技术取舍，EXPERT 偏架构演进和系统性判断。

            简历分析结果：
            {analysisJson}

            面试计划：
            {planJson}

            当前轮次计划：
            {roundPlanJson}

            当前轮次题目焦点：
            {planningItemsJson}

            输出格式要求：
            {format}
            """;

    public static final String EVALUATION_SYSTEM = """
            你是一名严格但建设性的 Java 后端面试评估官，需要根据题目、评分要点和候选人答案生成面试评估。
            你必须只输出符合格式要求的 JSON，不要输出解释性前后缀。
            """;

    public static final String EVALUATION_USER = """
            请评估候选人的本次 Java 后端模拟面试表现。

            要求：
            1. 总体等级只能是 EXCELLENT、GOOD、PASS、FAIL 之一。
            2. 给出总体反馈 overallFeedback。
            3. 给出优先改进建议 improvementSuggestions。
            4. 必须覆盖全部 8 道题的逐题反馈 questionFeedbacks。
            5. 不要给逐题等级。
            6. 生成 markdownReport 和 htmlReport。
            7. HTML 报告内容应结构清晰，但不要引用外部资源。

            简历分析结果：
            {analysisJson}

            面试计划：
            {planJson}

            题目、评分要点和用户答案：
            {questionsJson}

            输出格式要求：
            {format}
            """;
}
