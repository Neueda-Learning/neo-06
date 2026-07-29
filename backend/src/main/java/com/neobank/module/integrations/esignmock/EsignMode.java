package com.neobank.module.integrations.esignmock;

/**
 * UC 07's playback dial: how the mock plays the customer once an envelope is registered.
 *
 * @see EsignProviderConfig
 */
public enum EsignMode {

    /** Posts the auto-outcome back almost immediately — AC2's "reaches SIGNED within seconds". */
    INSTANT,

    /** Posts the auto-outcome after {@code delaySeconds} — AC3's aging-in-the-queue demo. */
    DELAYED,

    /** Posts nothing. The case stays PENDING until the expiry clock decides — AC5. */
    SILENT
}
