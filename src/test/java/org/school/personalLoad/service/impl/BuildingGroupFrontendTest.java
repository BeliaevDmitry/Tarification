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

        assertTrue(buildingsHtml.contains("Добавить адрес к существующему корпусу"));
        assertTrue(buildingsHtml.contains("Добавить новый самостоятельный корпус"));
        assertTrue(buildingsHtml.contains("Новая площадка создаётся пустой. Классы и метагруппы на неё автоматически не переводятся."));
        assertTrue(buildingsHtml.contains("Новый корпус появится как самостоятельная вкладка нагрузки. Создаётся пустым, без автоматического переноса классов и метагрупп."));
        assertTrue(buildingsHtml.contains("Список площадок / адресов корпусов"));
        assertTrue(buildingsHtml.contains("Редактирование площадки"));
        assertTrue(buildingsHtml.contains("Очистить список площадок"));
        assertTrue(buildingsHtml.contains("Удалить площадку"));
        assertTrue(buildingsHtml.contains("<th>Основной корпус</th><th>Площадка</th><th>Руководитель</th><th>Адрес</th><th>Действия</th>"));
        assertTrue(buildingsJs.contains("buildingGroupId: Number(form.get(\"buildingGroupId\")"));
        assertTrue(buildingsJs.contains("api(\"/api/buildings\", { method: \"POST\""));
        assertTrue(buildingsJs.contains("ui.buildingGroupForm?.addEventListener"));
        assertTrue(buildingsJs.contains("api(\"/api/building-groups\", { method: \"POST\""));
        assertTrue(buildingsJs.contains("await loadBuildingGroups();"));
        assertTrue(buildingsJs.contains("await loadBuildings();"));
        assertFalse(buildingsJs.contains("payload.code = String(selectedGroup?.code"));
        assertFalse(buildingsJs.contains("if (selectedGroup?.code) payload.code"));
    }
}
