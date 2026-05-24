package com.highway.agent.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("interview_question")
public class InterviewQuestion {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionId;

    private Integer roundNumber;

    private String roundName;

    private String difficulty;

    private Integer questionNumber;

    private String content;

    private String scoringPoints;

    private String userAnswer;

    private String feedback;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
