package com.highway.agent.interview.service;

import com.highway.agent.common.service.MinioService;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.highway.agent.interview.mapper.InterviewResumeMapper;
import com.highway.agent.interview.mapper.InterviewSessionMapper;
import com.highway.agent.interview.model.InterviewEnums.BizType;
import com.highway.agent.interview.model.InterviewEnums.ResumeStatus;
import com.highway.agent.interview.model.InterviewEnums.TaskType;
import com.highway.agent.interview.model.InterviewResume;
import com.highway.agent.interview.model.InterviewSession;
import com.highway.agent.interview.model.InterviewTask;
import com.highway.agent.interview.model.ReportResponse;
import com.highway.agent.interview.model.ResumeDetailResponse;
import com.highway.agent.interview.model.ResumeListResponse;
import com.highway.agent.interview.model.ResumeUploadResponse;
import com.highway.agent.interview.model.agent.InterviewPlanResult;
import com.highway.agent.interview.model.agent.ResumeAnalysisResult;
import com.highway.agent.interview.service.agent.InterviewPlanningAgent;
import com.highway.agent.interview.service.agent.ResumeAnalysisAgent;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.ByteArrayInputStream;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InterviewResumeService {

    private static final long MAX_UPLOAD_SIZE = 10L * 1024 * 1024;

    private final InterviewResumeMapper resumeMapper;
    private final InterviewSessionMapper sessionMapper;
    private final MinioService minioService;
    private final ResumeParsingService resumeParsingService;
    private final InterviewTaskService taskService;
    private final ResumeAnalysisAgent resumeAnalysisAgent;
    private final InterviewPlanningAgent interviewPlanningAgent;
    private final ObjectMapper objectMapper;

    public Mono<ResumeUploadResponse> upload(FilePart file) {
        validateFileName(file.filename());
        return DataBufferUtils.join(file.content())
                .publishOn(Schedulers.boundedElastic())
                .map(buffer -> uploadBuffer(file, buffer));
    }

    public ResumeDetailResponse analyze(Long id) {
        InterviewResume resume = requireResume(id);
        resume.setStatus(ResumeStatus.ANALYZING.name());
        resume.setFailureReason(null);
        resumeMapper.updateById(resume);

        int retryCount = taskService.nextRetryCount(BizType.RESUME, id, TaskType.RESUME_ANALYZE);
        InterviewTask task = taskService.createTask(BizType.RESUME, id, TaskType.RESUME_ANALYZE, retryCount, resume.getFileName());
        taskService.markRunning(task.getId());
        try {
            String textPath = resume.getContentTextPath();
            if (textPath == null || textPath.isBlank()) {
                textPath = resumeParsingService.parseAndSaveText(resume.getId(), resume.getFilePath(), resume.getFileName());
            }
            String resumeText = minioService.getObject(textPath);
            String agentInput = resumeParsingService.truncateForAgent(resumeText);
            ResumeAnalysisResult analysisResult = resumeAnalysisAgent.analyze(agentInput);
            InterviewPlanResult planResult = interviewPlanningAgent.plan(analysisResult, agentInput);

            String analysisPath = analysisResultPath(id);
            String planPath = "interview/resume/" + id + "/interview-plan.json";
            String reportMdPath = "interview/resume/" + id + "/analysis.md";
            String reportHtmlPath = "interview/resume/" + id + "/analysis.html";
            minioService.putObject(analysisPath, toJson(analysisResult));
            minioService.putObject(planPath, toJson(planResult));
            minioService.putObject(reportMdPath, renderAnalysisMarkdown(analysisResult, planResult));
            minioService.putObject(reportHtmlPath, renderAnalysisHtml(analysisResult, planResult));

            resume.setContentTextPath(textPath);
            resume.setStatus(ResumeStatus.ANALYZED.name());
            resume.setTechTags(String.join(" / ", analysisResult.techTags()));
            resume.setTargetPosition(analysisResult.targetPosition());
            resume.setAnalysisSummary(analysisResult.summary());
            resume.setInterviewPlanSummary(planResult.summary());
            resume.setInterviewPlanPath(planPath);
            resume.setReportMdPath(reportMdPath);
            resume.setReportHtmlPath(reportHtmlPath);
            resumeMapper.updateById(resume);
            taskService.markSuccess(task.getId(), "简历分析和面试计划生成完成");
        } catch (Exception e) {
            resume.setStatus(ResumeStatus.FAILED.name());
            resume.setFailureReason(e.getMessage());
            resumeMapper.updateById(resume);
            taskService.markFailed(task.getId(), e.getMessage());
        }
        return get(id);
    }

    public ResumeDetailResponse get(Long id) {
        InterviewResume resume = requireResume(id);
        List<ResumeDetailResponse.SessionSummary> recentSessions = sessionMapper.selectList(new LambdaQueryWrapper<InterviewSession>()
                        .eq(InterviewSession::getResumeId, id)
                        .orderByDesc(InterviewSession::getCreatedAt)
                        .last("LIMIT 3"))
                .stream()
                .map(session -> new ResumeDetailResponse.SessionSummary(
                        session.getId(),
                        session.getStatus(),
                        session.getOverallGrade(),
                        null,
                        null,
                        session.getFailureReason(),
                        session.getCreatedAt()
                ))
                .toList();
        return new ResumeDetailResponse(
                resume.getId(),
                resume.getFileName(),
                resume.getStatus(),
                resume.getTechTags(),
                resume.getTargetPosition(),
                resume.getAnalysisSummary(),
                resume.getInterviewPlanSummary(),
                resume.getFailureReason(),
                taskService.latestTask(BizType.RESUME, id, TaskType.RESUME_ANALYZE),
                recentSessions,
                resume.getCreatedAt(),
                resume.getUpdatedAt()
        );
    }

    public ResumeListResponse list(long page, long pageSize) {
        long safePage = Math.max(page, 1);
        long safePageSize = Math.min(Math.max(pageSize, 1), 100);
        Page<InterviewResume> result = resumeMapper.selectPage(Page.of(safePage, safePageSize), new LambdaQueryWrapper<InterviewResume>()
                .orderByDesc(InterviewResume::getCreatedAt));
        List<ResumeListResponse.Item> items = result.getRecords().stream()
                .map(resume -> new ResumeListResponse.Item(
                        resume.getId(),
                        resume.getFileName(),
                        resume.getStatus(),
                        resume.getTechTags(),
                        resume.getTargetPosition(),
                        resume.getAnalysisSummary(),
                        resume.getFailureReason(),
                        resume.getCreatedAt(),
                        resume.getUpdatedAt()
                ))
                .toList();
        return new ResumeListResponse(result.getCurrent(), result.getSize(), result.getTotal(), items);
    }

    public ReportResponse report(Long id) {
        InterviewResume resume = requireResume(id);
        if (resume.getReportHtmlPath() == null || resume.getReportHtmlPath().isBlank()) {
            throw new IllegalStateException("简历评估报告尚未生成");
        }
        return new ReportResponse(id, "html", minioService.getObject(resume.getReportHtmlPath()));
    }

    private ResumeUploadResponse uploadBuffer(FilePart file, DataBuffer buffer) {
        try {
            int size = buffer.readableByteCount();
            if (size == 0) {
                throw new IllegalArgumentException("简历文件不能为空");
            }
            if (size > MAX_UPLOAD_SIZE) {
                throw new IllegalArgumentException("简历文件不能超过 10MB");
            }
            byte[] bytes = new byte[size];
            buffer.read(bytes);

            InterviewResume resume = new InterviewResume();
            resume.setFileName(file.filename());
            resume.setFilePath("");
            resume.setStatus(ResumeStatus.UPLOADED.name());
            resumeMapper.insert(resume);

            String filePath = "interview/resume/" + resume.getId() + "/original/" + file.filename();
            String contentType = file.headers().getContentType() == null
                    ? "application/octet-stream"
                    : file.headers().getContentType().toString();
            minioService.putObject(filePath, new ByteArrayInputStream(bytes), bytes.length, contentType);
            resume.setFilePath(filePath);
            resumeMapper.updateById(resume);
            return new ResumeUploadResponse(resume.getId(), resume.getFileName(), resume.getStatus());
        } finally {
            DataBufferUtils.release(buffer);
        }
    }

    public static String analysisResultPath(Long resumeId) {
        return "interview/resume/" + resumeId + "/analysis-result.json";
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("面试分析结果序列化失败", e);
        }
    }

    private String renderAnalysisMarkdown(ResumeAnalysisResult analysisResult, InterviewPlanResult planResult) {
        return "# 简历评估\n\n" + analysisResult.summary() + "\n\n## 面试计划\n\n" + planResult.summary();
    }

    private String renderAnalysisHtml(ResumeAnalysisResult analysisResult, InterviewPlanResult planResult) {
        return "<h1>简历评估</h1><p>" + escapeHtml(analysisResult.summary()) + "</p><h2>面试计划</h2><p>" + escapeHtml(planResult.summary()) + "</p>";
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private InterviewResume requireResume(Long id) {
        InterviewResume resume = resumeMapper.selectById(id);
        if (resume == null) {
            throw new IllegalArgumentException("简历不存在: " + id);
        }
        return resume;
    }

    private void validateFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("简历文件不能为空");
        }
        String lowerFileName = fileName.toLowerCase();
        if (!(lowerFileName.endsWith(".pdf") || lowerFileName.endsWith(".doc") || lowerFileName.endsWith(".docx") || lowerFileName.endsWith(".txt"))) {
            throw new IllegalArgumentException("仅支持 PDF、DOC、DOCX 简历");
        }
    }
}
