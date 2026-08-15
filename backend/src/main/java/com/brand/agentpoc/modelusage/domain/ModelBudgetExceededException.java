package com.brand.agentpoc.modelusage.domain;

public class ModelBudgetExceededException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ModelBudgetExceededException() {
        super("The tenant model budget has been reached.");
    }
}
