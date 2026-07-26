package org.school.personalLoad.controller.api;

import org.junit.jupiter.api.Test;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PedagogicalCouncilControllerDateBindingTest {

    @Test
    void archiveUploadAcceptsIsoDateProducedByHtmlDateInput() throws Exception {
        Method method = PedagogicalCouncilController.class.getMethod(
                "uploadArchive",
                String.class,
                String.class,
                LocalDate.class,
                MultipartFile.class,
                HttpServletRequest.class
        );

        DateTimeFormat format = method.getParameters()[2].getAnnotation(DateTimeFormat.class);

        assertNotNull(format);
        assertEquals(DateTimeFormat.ISO.DATE, format.iso());
    }
}
