package com.neobank.module.model;

/**
 * UC 07 · Operate Mock Control Panel — how the e-sign mock plays the customer for the NEXT
 * envelope it registers.
 *
 * <ul>
 *   <li>{@code INSTANT} — posts {@link EsignOutcome} back within seconds.</li>
 *   <li>{@code DELAYED} — posts it after {@code delaySeconds}, so a case visibly ages in the
 *       queue before resolving.</li>
 *   <li>{@code SILENT} — posts nothing; the module's own expiry clock is what ends the case.</li>
 * </ul>
 */
public enum EsignMode {
    INSTANT,
    DELAYED,
    SILENT
}
