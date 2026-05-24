package com.highway.agent.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("interview_resume")
public class InterviewResume {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String fileName;

    private String filePath;

    private String contentTextPath;

    private String status;

    private String techTags;

    private String targetPosition;

    private String analysisSummary;

    private String interviewPlanSummary;

    private String interviewPlanPath;

    private String reportMdPath;

    private String reportHtmlPath;

    private String failureReason;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
