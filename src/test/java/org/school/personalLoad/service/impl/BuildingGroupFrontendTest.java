package org.school.personalLoad.service.impl;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildingGroupFrontendTest {

    @Test
    void buildingsPageSeparatesExistingAddressAndNewOrganizationalBuildingForms() throws Exception {
        String buildingsHtml = Files.readString(Path.of("src/main/resources/static/buildings.html"));
        String buildingsJs = Files.readString(Path.of("src/main/resources/static/buildings.js"));

        assertTrue(buildingsHtml.contains("Добавить физическую площадку к существующему основному корпусу"));
        assertTrue(buildingsHtml.contains("Добавить новый основной корпус / подразделение"));

        assertTrue(buildingsHtml.contains("Создать с новой физической площадкой"));
        assertTrue(buildingsHtml.contains("Создать без собственной площадки, использовать существующую площадку"));
        assertTrue(buildingsHtml.contains("Базовая физическая площадка"));
        assertTrue(buildingsHtml.contains("Если подразделение работает на базе уже существующего адреса"));
        assertTrue(buildingsJs.contains("createInitialSite = form.get(\"createInitialSite\") !== \"false\""));
        assertTrue(buildingsJs.contains("payload.baseSchoolBuildingId = Number(form.get(\"baseSchoolBuildingId\")"));
        assertTrue(buildingsJs.contains("fillBaseSiteSelect"));
        assertTrue(buildingsHtml.contains("Новая площадка создаётся пустой. Классы и метагруппы на неё автоматически не переводятся."));
        assertTrue(buildingsHtml.contains("Новый корпус появится как самостоятельная вкладка нагрузки. Создаётся пустым, без автоматического переноса классов и метагрупп."));
        assertTrue(buildingsHtml.contains("<h2>Корпуса</h2>"));
        assertFalse(buildingsHtml.contains("<h2>Физические площадки / адреса</h2>"));
        assertFalse(buildingsHtml.contains("id=\"buildings-body\""));
        assertTrue(buildingsJs.contains("Собственной площадки нет"));
        assertTrue(buildingsHtml.contains("Редактирование основного корпуса / подразделения"));
        assertTrue(buildingsHtml.contains("Очистить список площадок"));
        assertTrue(buildingsHtml.contains("name=\"code\" readonly"));
        assertTrue(buildingsHtml.contains("Режим физической площадки"));
        assertTrue(buildingsHtml.contains("<th>Код корпуса</th><th>Название корпуса</th><th>Руководитель / ответственный</th><th>Физическая площадка / адрес</th><th>Действия</th>"));
        assertFalse(buildingsHtml.contains("<th>Основной корпус-владелец площадки</th><th>Название площадки</th><th>Адрес</th><th>Руководитель площадки</th><th>Действия</th>"));
        assertTrue(buildingsJs.contains("buildingGroupId: Number(form.get(\"buildingGroupId\")"));
        assertTrue(buildingsJs.contains("api(\"/api/buildings\", { method: \"POST\""));
        assertTrue(buildingsJs.contains("ui.buildingGroupForm?.addEventListener"));
        assertTrue(buildingsJs.contains("api(\"/api/building-groups\", { method: \"POST\""));
        assertTrue(buildingsJs.contains("method: 'PATCH'"));
        assertTrue(buildingsJs.contains("name: String(ui.buildingGroupEditForm.elements.name.value"));
        assertFalse(buildingsJs.contains("code: String(ui.buildingGroupEditForm.elements.code.value"));
        assertTrue(buildingsJs.contains("renderBuildingGroups"));
        assertTrue(buildingsJs.contains("physicalSitesForGroupLabel"));
        assertTrue(buildingsJs.contains("await loadBuildingGroups();"));
        assertTrue(buildingsJs.contains("await loadBuildings();"));
        assertFalse(buildingsJs.contains("payload.code = String(selectedGroup?.code"));
        assertFalse(buildingsJs.contains("if (selectedGroup?.code) payload.code"));
    }
}
