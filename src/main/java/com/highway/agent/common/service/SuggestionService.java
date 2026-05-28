package com.highway.agent.common.service;

import com.highway.agent.common.service.PromptTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SuggestionService {

    private final ChatClient chatClient;
    private final PromptTemplate promptTemplate;

    public List<String> generateSuggestions(String userMessage, String assistantAnswer, int count) {
        String prompt = promptTemplate.getSuggestionPrompt(count, userMessage, assistantAnswer);

        try {
            String response = chatClient.prompt(prompt).call().content();
            return parseSuggestions(response);
        } catch (Exception e) {
            log.error("Failed to generate suggestions", e);
            return List.of();
        }
    }

    private List<String> parseSuggestions(String response) {
        return Arrays.stream(response.split("\n"))
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .map(line -> line.replaceAll("^\\d+[.、)\\s]+", ""))
                .toList();
    }
}
