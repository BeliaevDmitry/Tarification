package org.school.personalLoad.controller.api;

import lombok.Builder;
import lombok.Data;
import org.school.personalLoad.config.SchoolCodeResolver;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;


@RestController
@RequestMapping("/api/public/branding")
public class PublicBrandingController {

    @GetMapping
    public BrandingResponse get(HttpServletRequest request) {
        String schoolCode = SchoolCodeResolver.resolve(String.join(",",
                headerOrBlank(request, "X-Forwarded-Host"),
                headerOrBlank(request, "X-Original-Host"),
                headerOrBlank(request, "Host"),
                request.getServerName()));
        String crestUrl = "/school-crests/crest-" + schoolCode + ".png";
        String appTitle = "ГБОУ школа №" + schoolCode;
        String loginTitle = "Вход в систему ГБОУ школа №" + schoolCode;
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

    private String headerOrBlank(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        return value == null ? "" : value;
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
