package org.school.personalLoad.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@ConfigurationProperties(prefix = "app")
public class AppConfig {
    private String outputDirectory;
    private boolean keepHistory = true;
    private boolean clearHistoryOnStart;
    private Set<String> excludedTeachers = Set.of();

    public String getOutputDirectory() {
        return outputDirectory;
    }

    public void setOutputDirectory(String outputDirectory) {
        this.outputDirectory = outputDirectory;
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
