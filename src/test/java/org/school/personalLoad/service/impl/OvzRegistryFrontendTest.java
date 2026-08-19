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
        assertThat(page).contains("Для мультисортировки нажимайте заголовки по очереди");
        assertThat(script).contains("let ovzRegistrySort = []");
        assertThat(script).contains("String(item.fullName || '').toLocaleLowerCase('ru').includes(needle)");
        assertThat(script).contains("ovzRegistrySort.push({ key, direction: 'asc' })");
        assertThat(script).contains("const visibleStudentIds = registryRowsForView().map((item) => item.studentId)");
        assertThat(script).contains("method: 'POST'");
    }
}
