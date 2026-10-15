package com.project.onlinebankingsystem.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiAssistantRequest {
    @NotBlank(message = "Message query cannot be empty")
    private String query;

    private String conversationContext;

    public AiAssistantRequest(String query) {
        this.query = query;
    }
}
