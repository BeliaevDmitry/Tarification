package org.school.personalLoad.controller.api;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PublicBrandingControllerTest {

    @Test
    void schoolNumberIsIncludedInApplicationAndLoginTitles() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Host", "schadmin.ru");

        PublicBrandingController.BrandingResponse branding = new PublicBrandingController().get(request);

        assertEquals("ГБОУ школа №7", branding.getAppTitle());
        assertEquals("Вход в систему ГБОУ школа №7", branding.getLoginTitle());
    }
}
