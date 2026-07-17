package org.school.personalLoad.service.impl;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
class HrDocumentsFrontendTest {
 @Test void pageContainsDocumentJournalAndProtectedPersonalImport() throws Exception {String h=Files.readString(Path.of("src/main/resources/static/teachers-notification.html"));String js=Files.readString(Path.of("src/main/resources/static/teachers-notification.js"));assertTrue(h.contains("Кадровые документы"));assertTrue(h.contains("Персональные данные"));assertTrue(js.contains("/api/hr-documents/agreements"));assertTrue(js.contains("personal-data/import"));}
}
