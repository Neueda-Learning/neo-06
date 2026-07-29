package com.neobank.module.service;

import com.neobank.module.dto.EsignConfigView;

/**
 * The id a fresh {@code POST /envelopes} was given, plus the dial state that was in force AT
 * REGISTRATION TIME — captured once so a dial change made a moment later can only ever affect the
 * NEXT envelope, never one already in flight (UC 07 AC 6).
 */
public record EnvelopeRegistration(String envelopeId, EsignConfigView config) {
}
