package com.brand.agentpoc.observability.domain;

public enum OperationalEvent {
    DATA_IMPORT("agentpoc.data.import", "data.import", "dataimport"),
    REPORT_GENERATION("agentpoc.report.generate", "report.generate", "reporting"),
    REPORT_JOB_EXECUTION("agentpoc.report.job.execute", "report.job.execute", "reporting"),
    REPORT_DELIVERY("agentpoc.report.delivery", "report.delivery", "reporting"),
    REPORT_COLLABORATION_NOTIFICATION(
            "agentpoc.report.collaboration.notification",
            "report.collaboration.notification",
            "reporting"
    );

    private final String observationName;
    private final String eventName;
    private final String component;

    OperationalEvent(String observationName, String eventName, String component) {
        this.observationName = observationName;
        this.eventName = eventName;
        this.component = component;
    }

    public String observationName() {
        return observationName;
    }

    public String eventName() {
        return eventName;
    }

    public String component() {
        return component;
    }
}
