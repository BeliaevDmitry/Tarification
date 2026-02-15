package org.school.personalLoad.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;

@Component
@ConfigurationProperties(prefix = "app")
public class AppConfig {
    private static final DateTimeFormatter FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy HH-mm");

    private String sheetsUrl;
    private String tarifficationFilePrefix;
    private String practicumFileName;
    private String downloadDirectory;
    private String outputDirectory;
    private String offlineFilesDirectory;
    private String expelledFilePath;
    private boolean keepHistory = true;
    private boolean clearHistoryOnStart;
    private Set<String> excludedTeachers = Set.of();

    public String getTarifficationFileName() {
        return tarifficationFilePrefix + " " + LocalDateTime.now().format(FILE_DATE_FORMAT);
    }

    public String getTarifficationOutputPath() {
        return outputDirectory + "Тарификация" + LocalDateTime.now().format(FILE_DATE_FORMAT) + ".xlsx";
    }

    public String getPracticumOutputPath() {
        return outputDirectory + "контингент практикумы.xlsx";
    }

    public String getDownloadPath(String fileName) {
        return downloadDirectory + fileName;
    }

    public String getSheetsUrl() {
        return sheetsUrl;
    }

    public void setSheetsUrl(String sheetsUrl) {
        this.sheetsUrl = sheetsUrl;
    }

    public String getTarifficationFilePrefix() {
        return tarifficationFilePrefix;
    }

    public void setTarifficationFilePrefix(String tarifficationFilePrefix) {
        this.tarifficationFilePrefix = tarifficationFilePrefix;
    }

    public String getPracticumFileName() {
        return practicumFileName;
    }

    public void setPracticumFileName(String practicumFileName) {
        this.practicumFileName = practicumFileName;
    }

    public String getDownloadDirectory() {
        return downloadDirectory;
    }

    public void setDownloadDirectory(String downloadDirectory) {
        this.downloadDirectory = downloadDirectory;
    }

    public String getOutputDirectory() {
        return outputDirectory;
    }

    public void setOutputDirectory(String outputDirectory) {
        this.outputDirectory = outputDirectory;
    }

    public String getOfflineFilesDirectory() {
        return offlineFilesDirectory;
    }

    public void setOfflineFilesDirectory(String offlineFilesDirectory) {
        this.offlineFilesDirectory = offlineFilesDirectory;
    }

    public String getExpelledFilePath() {
        return expelledFilePath;
    }

    public void setExpelledFilePath(String expelledFilePath) {
        this.expelledFilePath = expelledFilePath;
    }

    public boolean isKeepHistory() {
        return keepHistory;
    }

    public void setKeepHistory(boolean keepHistory) {
        this.keepHistory = keepHistory;
    }

    public boolean isClearHistoryOnStart() {
        return clearHistoryOnStart;
    }

    public void setClearHistoryOnStart(boolean clearHistoryOnStart) {
        this.clearHistoryOnStart = clearHistoryOnStart;
    }

    public Set<String> getExcludedTeachers() {
        return excludedTeachers;
    }

    public void setExcludedTeachers(Set<String> excludedTeachers) {
        this.excludedTeachers = excludedTeachers;
    }
}
