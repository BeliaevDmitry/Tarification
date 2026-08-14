package org.school.personalLoad.service.impl;

import org.junit.jupiter.api.Test;
import org.school.personalLoad.controller.api.ApiExceptionHandler;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiExceptionHandlerTest {
    @Test
    void businessConflictIsNotConvertedToInternalServerError() {
        MockHttpServletRequest request=new MockHttpServletRequest("GET","/api/hr-documents/agreements/1/download");

        var response=new ApiExceptionHandler().handleResponseStatus(
                new ResponseStatusException(HttpStatus.CONFLICT,"Заполните трудовой договор"),request);

        assertEquals(HttpStatus.CONFLICT,response.getStatusCode());
        assertEquals("Заполните трудовой договор",response.getBody().getMessage());
    }

    @Test
    void oversizedMultipartRequestHasRussianPayloadTooLargeResponse() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/vsoko/mcko/imports");

        var response = new ApiExceptionHandler().handleMaxUploadSize(
                new MaxUploadSizeExceededException(35L * 1024 * 1024), request);

        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, response.getStatusCode());
        assertTrue(response.getBody().getMessage().contains("несколькими пакетами"));
        assertEquals("/api/vsoko/mcko/imports", response.getBody().getPath());
    }
}
