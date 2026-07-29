package org.school.personalLoad.pa.service.impl;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaStoragePathTest {

    private final Path directory = Path.of("pa-specifications", "2026-2027");

    @Test
    void keepsOrdinaryRussianFileNameInsideStorageDirectory() {
        Path result = PaStoragePath.resolveUploadedFile(directory, "Спецификация 7 класс.xlsx");

        assertEquals("Спецификация 7 класс.xlsx", result.getFileName().toString());
        assertTrue(result.startsWith(directory.toAbsolutePath().normalize()));
    }

    @Test
    void removesUnixAndWindowsTraversalFromSubmittedName() {
        Path unix = PaStoragePath.resolveUploadedFile(directory, "../../outside.xlsx");
        Path windows = PaStoragePath.resolveUploadedFile(directory, "..\\..\\outside.xlsx");

        assertEquals("outside.xlsx", unix.getFileName().toString());
        assertEquals("outside.xlsx", windows.getFileName().toString());
        assertTrue(unix.startsWith(directory.toAbsolutePath().normalize()));
        assertTrue(windows.startsWith(directory.toAbsolutePath().normalize()));
    }

    @Test
    void rejectsEmptyAndDirectoryOnlyNames() {
        assertThrows(IllegalArgumentException.class,
                () -> PaStoragePath.resolveUploadedFile(directory, ""));
        assertThrows(IllegalArgumentException.class,
                () -> PaStoragePath.resolveUploadedFile(directory, "../"));
    }
}
