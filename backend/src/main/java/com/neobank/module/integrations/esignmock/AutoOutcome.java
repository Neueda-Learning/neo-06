package com.neobank.module.integrations.esignmock;

import com.neobank.module.model.SignatureEventType;

/**
 * UC 07's outcome dial: which signature event {@link EsignMode#INSTANT}/{@link EsignMode#DELAYED}
 * auto-post.
 *
 * <p>Deliberately a distinct enum from {@link SignatureEventType} rather than reusing it: this is
 * the mock's own vocabulary for an instruction ("go {@code SIGN} the next envelope"), matching the
 * doc's own contract example ({@code "autoOutcome":"SIGN"}) — {@link SignatureEventType} is the
 * fact the mock then reports having happened ({@code SIGNED}), which is a different value in the
 * doc's own wording even though the two are obviously related. {@link #toSignatureEventType()} is
 * the one place that relationship is spelled out.</p>
 */
public enum AutoOutcome {

    SIGN,
    DECLINE;

    public SignatureEventType toSignatureEventType() {
        return this == SIGN ? SignatureEventType.SIGNED : SignatureEventType.DECLINED;
    }
}
