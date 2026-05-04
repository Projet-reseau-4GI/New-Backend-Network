package yowyob.comops.api.file.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "iwm.file.storage")
public class FileStorageProperties {
    private String rootPath = "./data/files";
    private long maxFileSizeBytes = 10 * 1024 * 1024;
    private List<String> allowedContentTypes = new ArrayList<>();

    public String getRootPath() {
        return rootPath;
    }

    public void setRootPath(String rootPath) {
        this.rootPath = rootPath;
    }

    public long getMaxFileSizeBytes() {
        return maxFileSizeBytes;
    }

    public void setMaxFileSizeBytes(long maxFileSizeBytes) {
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    public List<String> getAllowedContentTypes() {
        return allowedContentTypes;
    }

    public void setAllowedContentTypes(List<String> allowedContentTypes) {
        this.allowedContentTypes = allowedContentTypes == null ? new ArrayList<>() : new ArrayList<>(allowedContentTypes);
    }
}
