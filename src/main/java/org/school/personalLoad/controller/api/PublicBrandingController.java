package org.school.personalLoad.controller.api;

import lombok.Builder;
import lombok.Data;
import org.school.personalLoad.config.SchoolCodeResolver;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/public/branding")
public class PublicBrandingController {

    @GetMapping
    public BrandingResponse get() {
        String schoolCode = SchoolCodeResolver.resolve();
        String crestUrl = "/school-crests/crest-" + schoolCode + ".png";
        String appTitle = "ГБОУ школа " + schoolCode;
        String loginTitle = "Вход в систему ГБОУ №" + schoolCode;
        String welcome = "Выберите рабочий контур системы.";
        return BrandingResponse.builder()
                .schoolCode(schoolCode)
                .crestUrl(crestUrl)
                .fallbackCrestUrl("/school-crest.png")
                .appTitle(appTitle)
                .loginTitle(loginTitle)
                .welcomeText(welcome)
                .build();
    }

    @Data
    @Builder
    public static class BrandingResponse {
        private String schoolCode;
        private String crestUrl;
        private String fallbackCrestUrl;
        private String appTitle;
        private String loginTitle;
        private String welcomeText;
    }
}
