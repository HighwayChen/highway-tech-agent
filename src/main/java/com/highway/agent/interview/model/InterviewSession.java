package com.highway.agent.interview.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("interview_session")
public class InterviewSession {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long resumeId;

    private String status;

    private String interviewPlanPath;

    private String overallGrade;

    private String overallFeedback;

    private String improvementSuggestions;

    private String reportMdPath;

    private String reportHtmlPath;

    private String failureReason;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
