package com.highway.agent.service;

import lombok.RequiredArgsConstructor;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;

@Service
@RequiredArgsConstructor
public class ResumeParsingService {

    private static final int MIN_TEXT_LENGTH = 100;
    private static final int AGENT_INPUT_LIMIT = 12_000;

    private final MinioService minioService;
    private final Tika tika = new Tika();

    public String parseAndSaveText(Long resumeId, String filePath, String fileName) {
        try {
            byte[] content = minioService.getObjectBytes(filePath);
            String parsedText = tika.parseToString(new ByteArrayInputStream(content));
            String cleanedText = cleanText(parsedText);
            if (cleanedText.length() < MIN_TEXT_LENGTH) {
                throw new IllegalArgumentException("简历文本内容过短，无法分析: " + fileName);
            }
            String textPath = "interview/resume/" + resumeId + "/content.txt";
            minioService.putObject(textPath, cleanedText);
            return textPath;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("简历解析失败: " + fileName, e);
        }
    }

    public String truncateForAgent(String text) {
        if (text == null || text.length() <= AGENT_INPUT_LIMIT) {
            return text;
        }
        return text.substring(0, AGENT_INPUT_LIMIT);
    }

    private String cleanText(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("\\s+", " ").trim();
    }
}
