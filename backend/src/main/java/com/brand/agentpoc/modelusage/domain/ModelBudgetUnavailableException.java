package com.brand.agentpoc.modelusage.domain;

public class ModelBudgetUnavailableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ModelBudgetUnavailableException(Throwable cause) {
        super("Model budget admission is unavailable.", cause);
    }
}
