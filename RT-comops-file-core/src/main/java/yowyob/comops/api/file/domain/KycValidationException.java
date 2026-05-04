package yowyob.comops.api.file.domain;

import java.util.List;

public class KycValidationException extends RuntimeException {
    private final List<String> reasons;

    public KycValidationException(List<String> reasons) {
        super("KYC validation failed: " + reasons);
        this.reasons = reasons;
    }

    public List<String> getReasons() {
        return reasons;
    }
}