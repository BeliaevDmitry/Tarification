package org.school.personalLoad.service.impl;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class OvzRegistryFrontendTest {

    @Test
    void registrySupportsFioSearchMultiSortAndVisibleRowsExport() throws Exception {
        String page = Files.readString(Path.of("src/main/resources/static/ovz.html"));
        String script = Files.readString(Path.of("src/main/resources/static/ovz.js"));

        assertThat(page).contains("placeholder=\"Поиск по ФИО\"");
        assertThat(page).contains("data-ovz-registry-sort=\"fullName\"");
        assertThat(page).contains("data-ovz-registry-sort=\"className\"");
        assertThat(page).contains("data-ovz-registry-sort=\"mseValidFrom\"");
        assertThat(page).contains("data-ovz-registry-sort=\"conclusionValidFrom\"");
        assertThat(page).contains("data-ovz-registry-sort=\"nosologyCode\"");
        assertThat(page).doesNotContain("data-ovz-registry-sort=\"validTo\"");
        assertThat(page).doesNotContain("data-ovz-registry-sort=\"studentId\"");
        assertThat(page).contains("Для мультисортировки нажимайте заголовки по очереди");
        assertThat(page).contains("id=\"ovz-registry-stat-mse-only\"");
        assertThat(page).contains("id=\"ovz-registry-stat-conclusion-only\"");
        assertThat(page).contains("id=\"ovz-registry-stat-mse-conclusion\"");
        assertThat(page).contains("id=\"ovz-registry-stat-recommendation\"");
        assertThat(script).contains("let ovzRegistrySort = []");
        assertThat(script).contains("String(item.fullName || '').toLocaleLowerCase('ru').includes(needle)");
        assertThat(script).contains("ovzRegistrySort.push({ key, direction: 'asc' })");
        assertThat(script).contains("const visibleStudentIds = registryRowsForView().map((item) => item.studentId)");
        assertThat(script).doesNotContain("<td>${item.studentId}</td>");
        assertThat(script).contains("item.nosologyCode || '—'");
        assertThat(script).contains("ovzDocumentPeriod(item.mse, item.mseValidFrom, item.mseValidTo)");
        assertThat(script).contains("ovzDocumentPeriod(item.conclusion, item.conclusionValidFrom, item.conclusionValidTo)");
        assertThat(script).contains("params.set('nosologyCode'");
        assertThat(script).contains("const selectedProgram = ovzUi.education_program.value");
        assertThat(script).contains("ovzUi.education_program.value = selectedProgram");
        assertThat(script).contains("ovzUi.education_program_other.value = customProgram");
        assertThat(script).contains("method: 'POST'");
        assertThat(script).contains("item.mse && !item.conclusion && !item.recommendation");
        assertThat(script).contains("item.conclusion && !item.mse && !item.recommendation");
        assertThat(script).contains("item.mse && item.conclusion");
        assertThat(script).contains("item.recommendation).length");
        assertThat(script).contains("function isMseOnly(item)");
        assertThat(script).contains("isMseOnly(item) ? '<span class=\"muted\"");
        assertThat(script).contains("<strong>Далее</strong><small>Не требуется</small>");
        assertThat(script).contains("<strong>Далее</strong><span>Не требуется</span>");
    }
}
