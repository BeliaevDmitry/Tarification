package org.school.personalLoad.service;

import org.school.personalLoad.model.GroupOrClassInfo;
import org.school.personalLoad.service.impl.GroupSearchServiceImpl;

import java.util.List;
import java.util.Map;

/**
 * Интерфейс сервиса для поиска групп и сбора информации о классах
 */
public interface GroupSearchService {

    /**
     * Ищет группы для студентов-инвалидов в офлайн файлах
     *
     * @param onlineFilePath путь к онлайн файлу с данными об инвалидах
     * @param offlineFolderPath путь к папке с офлайн файлами
     * @return карта с именами студентов и списками их групп
     * @throws Exception если произошла ошибка при обработке файлов
     */
    Map<String, List<String>> findGroupsForDisabledStudents(String onlineFilePath,
                                                            String offlineFolderPath) throws Exception;

    /**
     * Собирает информацию о классах, численности и преподавателях из офлайн файлов
     *
     * @param offlineFolderPath путь к папке с офлайн файлами
     * @return карта с информацией о классах
     * @throws Exception если произошла ошибка при обработке файлов
     */
    Map<String, GroupOrClassInfo> collectClassInfo(String offlineFolderPath) throws Exception;

    /**
     * Вспомогательный класс для хранения информации о классе
     */
    class ClassInfo {
        private final String className;
        private final int studentCount;
        private final String teacherName;

        public ClassInfo(String className, int studentCount, String teacherName) {
            this.className = className;
            this.studentCount = studentCount;
            this.teacherName = teacherName;
        }

        public String getClassName() { return className; }
        public int getStudentCount() { return studentCount; }
        public String getTeacherName() { return teacherName; }

        @Override
        public String toString() {
            return "ClassInfo{" +
                    "className='" + className + '\'' +
                    ", studentCount=" + studentCount +
                    ", teacherName='" + teacherName + '\'' +
                    '}';
        }
    }
}