package com.brand.agentpoc.organization.domain;

public enum OrganizationNodeType {
    GROUP(0),
    REGION(1),
    CITY(2),
    DEALER(3);

    private final int level;

    OrganizationNodeType(int level) {
        this.level = level;
    }

    public boolean acceptsChild(OrganizationNodeType childType) {
        return childType != null && childType.level == level + 1;
    }
}
