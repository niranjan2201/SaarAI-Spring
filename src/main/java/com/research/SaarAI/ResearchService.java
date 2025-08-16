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

            case "format":
                prompt.append(
                        "Summarize the given text in Samsung-style headers and bullets. " +
                                "Output ONLY valid HTML using this EXACT structure:\n\n" +
                                "<h2>[Main Title]</h2>" +
                                "<section>" +
                                "  <h3>[Section Header]</h3>" +
                                "  <ul>" +
                                "    <li>Bullet point</li>" +
                                "    <li>Bullet point</li>" +
                                "  </ul>" +
                                "</section>" +
                                "Rules:\n" +
                                "- The <h2> is the main title of the summary.\n" +
                                "- All <h2> and <h3> headings must be visually styled in blue (handled by CSS, no inline styles).\n" +
                                "- Use multiple <section> blocks for each topic.\n" +
                                "- Headers must be short and clear; keep as given, no extra text.\n" +
                                "- Each bullet is concise, factual, and derived from the input.\n" +
                                "- For basic facts, use 'Label: Value' inside <li> (e.g., <li>Founded: 7 August 2015</li>).\n" +
                                "- Do NOT output any wrapper HTML (no <html>, <body>), ONLY sections and headings.\n" +
                                "- Do NOT add inline styles — just plain HTML tags.\n\n" +
                                "Output ONLY HTML with no markdown, no triple backticks, and no code block labels.\n" +
                                "Now output ONLY the HTML per the rules:\n\n"
                );
                break;



            case "meetingNotes":
                prompt.append(
                        "Summarize the following text in the style of Samsung Galaxy AI Meeting Notes. " +
                                "Follow this exact format:\n\n" +
                                "[Main Heading]\n" +
                                "• Bullet point\n" +
                                "• Bullet point\n\n" +
                                "[Next Heading]\n" +
                                "• Bullet point\n" +
                                "• Bullet point\n\n" +
                                "Rules:\n" +
                                "- Keep headings short and clear.\n" +
                                "- Use concise factual sentences without filler.\n" +
                                "- Preserve key details, figures, dates, and names exactly as given.\n" +
                                "- Do not rewrite in paragraphs.\n\n" +
                                "Now, summarize:\n\n"
                );
                break;


            default:
                throw new IllegalArgumentException("Unknown Operation: " + request.getOperation());
        }
        prompt.append(request.getContent());
        return prompt.toString();
    }


}
