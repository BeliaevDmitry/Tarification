package org.school.personalLoad.controller.api;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.school.personalLoad.model.PublicChatMessage;
import org.school.personalLoad.repository.PublicChatMessageRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/api/public/chat/messages")
@RequiredArgsConstructor
public class PublicChatController {

    private final PublicChatMessageRepository repository;

    @GetMapping
    public List<PublicChatMessage> list() {
        List<PublicChatMessage> result = repository.findTop100ByOrderByIdDesc();
        Collections.reverse(result);
        return result;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PublicChatMessage create(@RequestBody CreateMessageRequest request) {
        String author = normalized(request.getAuthor(), "Укажите имя", 40);
        String text = normalized(request.getText(), "Напишите сообщение", 1000);

        PublicChatMessage message = new PublicChatMessage();
        message.setAuthor(author);
        message.setText(text);
        message.setCreatedAt(Instant.now());
        return repository.save(message);
    }

    private String normalized(String value, String emptyMessage, int maxLength) {
        String result = value == null ? "" : value.trim();
        if (result.isEmpty()) {
            throw new IllegalArgumentException(emptyMessage);
        }
        if (result.length() > maxLength) {
            throw new IllegalArgumentException("Слишком длинный текст");
        }
        return result;
    }

    @Data
    public static class CreateMessageRequest {
        private String author;
        private String text;
    }
}
