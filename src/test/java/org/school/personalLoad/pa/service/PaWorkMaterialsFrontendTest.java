package org.school.personalLoad.pa.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PaWorkMaterialsFrontendTest {

    @Test
    void protectedWorkspaceAcceptsOneOrSeveralFilesAndShowsUploaderDetails() throws Exception {
        String html = Files.readString(Path.of("src/main/resources/static/vsoko-pa-materials.html"));
        String script = Files.readString(Path.of("src/main/resources/static/vsoko-pa-materials.js"));
        String hub = Files.readString(Path.of("src/main/resources/static/vsoko-pa.html"));
        String auth = Files.readString(Path.of("src/main/resources/static/auth.js"));

        assertTrue(html.contains("Тексты работ"));
        assertTrue(html.contains("Ответы"));
        assertTrue(html.contains("Свод текстов 5–11"));
        assertTrue(html.contains("Конкретный класс"));
        assertTrue(html.contains("name=\"textFiles\"") && html.contains("name=\"answerFiles\""));
        assertTrue(html.contains("multiple"));
        assertTrue(!html.contains("Точное количество вариантов"));
        assertTrue(script.contains("file.uploadedByFio"));
        assertTrue(script.contains("field:'textFiles'") && script.contains("field:'answerFiles'"));
        assertTrue(script.contains("form.append(item.field,item.file)"));
        assertTrue(script.contains("Загрузка ${index+1} из ${queue.length}"));
        assertTrue(script.contains("/api/pa/materials"));
        assertTrue(hub.contains("/vsoko-pa-materials.html"));
        assertTrue(auth.contains("label: 'Тексты работ'"));
    }

    @Test
    void publicPageOffersIndividualFilesAndOneZipWithoutAuthScript() throws Exception {
        String html = Files.readString(Path.of("src/main/resources/static/pa-materials.html"));
        String script = Files.readString(Path.of("src/main/resources/static/pa-materials.js"));

        assertTrue(html.contains("Публичная страница"));
        assertTrue(html.contains("Скачать всё одним ZIP"));
        assertTrue(script.contains("/api/public/pa/materials"));
        assertTrue(script.contains("/api/public/pa/materials/files/"));
        assertTrue(script.contains("row.textFiles"));
        assertTrue(script.contains("row.answerFiles"));
        assertTrue(!html.contains("/auth.js"));
    }
}
