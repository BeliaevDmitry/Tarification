package org.school.personalLoad.controller.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.school.personalLoad.config.auth.AuthFilter;
import org.school.personalLoad.model.PublicChatMessage;
import org.school.personalLoad.repository.PublicChatMessageRepository;
import org.school.personalLoad.service.auth.AppUserService;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.servlet.FilterChain;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PublicChatControllerTest {

    @Test
    void guestCanOpenPageReadAndPostMessagesWithoutSession() throws Exception {
        AppUserService users = mock(AppUserService.class);
        AuthFilter filter = new AuthFilter(new ObjectMapper(), users);

        for (MockHttpServletRequest request : new MockHttpServletRequest[]{
                new MockHttpServletRequest("GET", "/public-chat.html"),
                new MockHttpServletRequest("GET", "/public-chat.js"),
                new MockHttpServletRequest("GET", "/public-questions.html"),
                new MockHttpServletRequest("GET", "/public-questions.js"),
                new MockHttpServletRequest("GET", "/public-questions-data.js"),
                new MockHttpServletRequest("GET", "/api/public/chat/messages"),
                new MockHttpServletRequest("POST", "/api/public/chat/messages")
        }) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            RecordingFilterChain chain = new RecordingFilterChain();

            filter.doFilter(request, response, chain);

            assertTrue(chain.called, request.getRequestURI() + " должен быть доступен без авторизации");
            assertEquals(200, response.getStatus());
        }
        verifyNoInteractions(users);
    }

    @Test
    void messageIsTrimmedAndSaved() {
        PublicChatMessageRepository repository = mock(PublicChatMessageRepository.class);
        when(repository.save(any(PublicChatMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        PublicChatController controller = new PublicChatController(repository);
        PublicChatController.CreateMessageRequest request = new PublicChatController.CreateMessageRequest();
        request.setAuthor("  Гость  ");
        request.setText("  Всем привет!  ");

        PublicChatMessage saved = controller.create(request);

        assertEquals("Гость", saved.getAuthor());
        assertEquals("Всем привет!", saved.getText());
        assertNotNull(saved.getCreatedAt());
    }

    @Test
    void emptyMessageIsRejected() {
        PublicChatController controller = new PublicChatController(mock(PublicChatMessageRepository.class));
        PublicChatController.CreateMessageRequest request = new PublicChatController.CreateMessageRequest();
        request.setAuthor("Гость");
        request.setText("   ");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> controller.create(request));

        assertEquals("Напишите сообщение", error.getMessage());
    }

    private static class RecordingFilterChain implements FilterChain {
        private boolean called;

        @Override
        public void doFilter(javax.servlet.ServletRequest request, javax.servlet.ServletResponse response) {
            called = true;
        }
    }
}
