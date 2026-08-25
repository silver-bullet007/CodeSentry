package com.codesentry.codesentry.model;

import java.util.List;

public record CodeReview(
        String summary,
        List<Issue> issues,
        Rating overallRating) {
    public record Issue(
            String description,
            Severity severity,
            String suggestion) {
    }

    public enum Severity {
        LOW, MEDIUM, HIGH
    }

    public enum Rating {
        GOOD, NEEDS_WORK, POOR
    }

}
