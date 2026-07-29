package com.neobank.module.integrations.esignmock;

/**
 * UC 07's dials — the doc's {@code EsignProviderConfig} entity, kept in memory rather than in a
 * table (see {@link EsignProviderConfigService}'s javadoc for why).
 *
 * @param mode              INSTANT, DELAYED or SILENT
 * @param delaySeconds      how long DELAYED waits before posting; ignored by INSTANT/SILENT
 * @param autoOutcome       SIGN or DECLINE — which event INSTANT/DELAYED post
 * @param demoExpirySeconds overrides the default expiry window for the NEXT envelope only;
 *                          {@code null} means "use the default" (see
 *                          {@link InMemoryEsignMockClient#DEFAULT_EXPIRY_SECONDS})
 */
public record EsignProviderConfig(
        EsignMode mode,
        int delaySeconds,
        AutoOutcome autoOutcome,
        Integer demoExpirySeconds) {

    /** The doc's own contract example: {@code {"mode":"INSTANT","delaySeconds":0,...}}. */
    public static EsignProviderConfig defaults() {
        return new EsignProviderConfig(EsignMode.INSTANT, 0, AutoOutcome.SIGN, null);
    }
}
