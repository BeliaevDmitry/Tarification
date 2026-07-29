package org.school.personalLoad.pa.service.impl;

import java.nio.file.Path;

final class PaStoragePath {

    private PaStoragePath() {
    }

    static Path resolveUploadedFile(Path directory, String submittedFileName) {
        if (submittedFileName == null || submittedFileName.isBlank()) {
            throw new IllegalArgumentException("Имя файла не указано");
        }
        String normalizedSeparators = submittedFileName.replace('\\', '/');
        int lastSeparator = normalizedSeparators.lastIndexOf('/');
        String fileName = (lastSeparator >= 0
                ? normalizedSeparators.substring(lastSeparator + 1)
                : normalizedSeparators)
                .replaceAll("[\\p{Cntrl}/:*?\"<>|]", "_")
                .trim();
        if (fileName.isBlank() || ".".equals(fileName) || "..".equals(fileName)) {
            throw new IllegalArgumentException("Некорректное имя файла");
        }

        Path base = directory.toAbsolutePath().normalize();
        Path target = base.resolve(fileName).normalize();
        if (!target.startsWith(base)) {
            throw new IllegalArgumentException("Некорректный путь к файлу");
        }
        return target;
    }
}
