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
        assertTrue(html.contains("Количество вариантов"));
        assertTrue(html.contains("/instructions/pa-methodist-instruction.docx"));
        assertTrue(Files.isRegularFile(Path.of("src/main/resources/static/instructions/pa-methodist-instruction.docx")));
        assertTrue(html.contains("id=\"pa-material-variant-count\""));
        assertTrue(script.contains("file.uploadedByFio"));
        assertTrue(script.contains("form.append('textFiles',file)") && script.contains("form.append('answerFiles',file)"));
        assertTrue(script.contains("form.set('variantCount'"));
        assertTrue(script.contains("Отправка комплекта"));
        assertTrue(script.contains("/api/pa/materials"));
        assertTrue(html.contains("id=\"pa-material-edit-dialog\""));
        assertTrue(html.contains("Заменить все ранее загруженные тексты"));
        assertTrue(html.contains("Заменить все ранее загруженные ответы"));
        assertTrue(script.contains("data-material-edit"));
        assertTrue(script.contains("method:'PUT'"));
        assertTrue(script.contains("replaceTextFiles"));
        assertTrue(script.contains("replaceAnswerFiles"));
        assertTrue(html.contains("id=\"pa-material-edit-delete\""));
        assertTrue(script.contains("method:'DELETE'"));
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
        assertTrue(script.contains("row.variantCount"));
        assertTrue(!html.contains("/auth.js"));
    }
}
