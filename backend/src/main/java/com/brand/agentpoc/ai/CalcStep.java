package com.brand.agentpoc.ai;

/**
 * A single step in the visible-thinking calculation trace.
 * Each step carries a bilingual label (what was done) and bilingual detail (what was found).
 */
public record CalcStep(
        String labelZh,
        String labelEn,
        String detailZh,
        String detailEn
) {
    public CalcStep {
        labelZh = labelZh == null ? "" : labelZh;
        labelEn = labelEn == null ? "" : labelEn;
        detailZh = detailZh == null ? "" : detailZh;
        detailEn = detailEn == null ? "" : detailEn;
    }
}
