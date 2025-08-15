package com.research.SaarAI;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;


@Service
public class ResearchService {

    @Value("${gemini.api.url}")
    private String geminiApiUrl;

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public ResearchService(WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
        this.webClient = webClientBuilder.build();
        this.objectMapper = objectMapper;
    }


    public String processContent(ResearchRequest request) {
        //Build the prompt:
            String prompt = buildPrompt(request);

        //Query the Ai Model API:
        Map<String,Object> requestBody = Map.of(
                "contents",new Object[]{
                    Map.of("parts" ,new Object[] {
                            Map.of("text",prompt)
                    })
                }
        );

        String response = webClient.post()
                .uri(geminiApiUrl + "?key=" + geminiApiKey)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();


        //Parse the response:
        //return the response:
        // Extract raw AI text
        String rawText = extractTextFromResponse(response);

        // ✅ Clean markdown before returning
        return cleanMarkdown(rawText);
    }

    private String cleanMarkdown(String text) {
        return text
                .replaceAll("(?m)^#{1,6}\\s*", "")   // remove headings #
                .replaceAll("\\*\\*(.*?)\\*\\*", "$1") // remove bold
                .replaceAll("\\*", "-");              // convert bullets
    }

    private String extractTextFromResponse(String response){
        try {
            GeminiResponse geminiResponse = objectMapper.readValue(response,GeminiResponse.class);
            if (geminiResponse.getCandidates() != null && !geminiResponse.getCandidates().isEmpty()){
                GeminiResponse.Candidate firstCandidate = geminiResponse.getCandidates().get(0);
                if (firstCandidate.getContent() !=null && firstCandidate.getContent().getParts() != null
                        && !firstCandidate.getContent().getParts().isEmpty()){
                    return firstCandidate.getContent().getParts().get(0).getText();
                }
            }
            return "No content found in response";
        }catch (Exception e){
            return "Error Parsing: " + e.getMessage();
        }
    }



    private String buildPrompt(ResearchRequest request) throws IllegalArgumentException {
        StringBuilder prompt = new StringBuilder();
        switch (request.getOperation()) {
            case "summarize":
                prompt.append("Please summarize the following text in a clear, concise, and easy-to-understand way. Highlight the main ideas and essential details, while keeping the overall context intact:\n\n");
                break;


            case "suggest":
                prompt.append("Based on the following content: suggest related topics and further reading. ")
                        .append("Format the response in markdown with clear headings (##) and bullet points (*). ")
                        .append("Use bold for main categories and keep lists concise.\n\n");
                break;

            default:
                throw new IllegalArgumentException("Unknown Operation: " + request.getOperation());
        }
        prompt.append(request.getContent());
        return prompt.toString();
    }


}
