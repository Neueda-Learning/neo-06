package com.neobank.module.integrations.esignmock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.neobank.module.dto.SignatureEventRequest;
import com.neobank.module.model.SignatureEventType;
import com.neobank.module.service.SignatureEventService;
import java.time.Instant;
import java.util.concurrent.ScheduledFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.TaskScheduler;

/**
 * UC 07's mock behaviour, mode by mode. Uses a real {@link EsignProviderConfigService} (it is a
 * trivial in-memory holder — mocking it would just re-describe it) and mocks the two things that
 * actually do work: the scheduler and {@link SignatureEventService}.
 */
class InMemoryEsignMockClientTest {

    private EsignProviderConfigService config;
    private SignatureEventService signatureEvents;
    private TaskScheduler scheduler;
    private InMemoryEsignMockClient client;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        config = new EsignProviderConfigService();
        signatureEvents = mock(SignatureEventService.class);
        scheduler = mock(TaskScheduler.class);
        when(scheduler.schedule(any(Runnable.class), any(Instant.class)))
                .thenReturn(mock(ScheduledFuture.class));
        client = new InMemoryEsignMockClient(config, signatureEvents, scheduler);
    }

    @Test
    void instantModeSchedulesTheAutoOutcomeForEssentiallyNow() {
        config.apply(new EsignProviderConfig(EsignMode.INSTANT, 0, AutoOutcome.SIGN, null));
        Instant before = Instant.now();

        client.registerEnvelope("app-1");

        ArgumentCaptor<Instant> scheduledAt = ArgumentCaptor.forClass(Instant.class);
        verify(scheduler).schedule(any(Runnable.class), scheduledAt.capture());
        // "essentially now" — a small fixed floor (not "a couple of seconds from now"), enough
        // margin for the caller to finish stamping envelopeId onto the AgreementRecord before
        // this fires — see this class's own comment on the race that floor exists to avoid.
        assertThat(scheduledAt.getValue()).isBetween(before, before.plusSeconds(2));
    }

    @Test
    void delayedModeSchedulesTheAutoOutcomeAfterDelaySeconds() {
        config.apply(new EsignProviderConfig(EsignMode.DELAYED, 20, AutoOutcome.SIGN, null));
        Instant before = Instant.now();

        client.registerEnvelope("app-2");

        ArgumentCaptor<Instant> scheduledAt = ArgumentCaptor.forClass(Instant.class);
        verify(scheduler).schedule(any(Runnable.class), scheduledAt.capture());
        assertThat(scheduledAt.getValue()).isBetween(before.plusSeconds(19), before.plusSeconds(21));
    }

    @Test
    void silentModeSchedulesNothingAtAll() {
        config.apply(new EsignProviderConfig(EsignMode.SILENT, 0, AutoOutcome.SIGN, 30));

        client.registerEnvelope("app-3");

        verifyNoInteractions(scheduler);
    }

    @Test
    void autoOutcomeDeclinePostsADeclinedEventWhenTheScheduledTaskFires() {
        config.apply(new EsignProviderConfig(EsignMode.INSTANT, 0, AutoOutcome.DECLINE, null));

        client.registerEnvelope("app-4");

        Runnable scheduledTask = captureScheduledTask();
        scheduledTask.run(); // simulate the scheduler firing

        ArgumentCaptor<SignatureEventRequest> posted = ArgumentCaptor.forClass(SignatureEventRequest.class);
        verify(signatureEvents).apply(org.mockito.ArgumentMatchers.eq("app-4"), posted.capture());
        assertThat(posted.getValue().event()).isEqualTo(SignatureEventType.DECLINED);
        assertThat(posted.getValue().envelopeId()).startsWith("env-");
    }

    @Test
    void aDialChangeAfterRegistrationNeverAffectsTheAlreadyRegisteredEnvelope() {
        // AC6: dials apply to the NEXT envelope only.
        config.apply(new EsignProviderConfig(EsignMode.INSTANT, 0, AutoOutcome.SIGN, null));

        client.registerEnvelope("app-5");
        Runnable firstScheduledTask = captureScheduledTask();

        // Change the dial to DECLINE before the first envelope's scheduled task fires.
        config.apply(new EsignProviderConfig(EsignMode.INSTANT, 0, AutoOutcome.DECLINE, null));
        firstScheduledTask.run();

        ArgumentCaptor<SignatureEventRequest> posted = ArgumentCaptor.forClass(SignatureEventRequest.class);
        verify(signatureEvents).apply(org.mockito.ArgumentMatchers.eq("app-5"), posted.capture());
        assertThat(posted.getValue().event()).isEqualTo(SignatureEventType.SIGNED);
    }

    @Test
    void anExceptionFromSignatureEventServiceIsSwallowedNotPropagated() {
        // e.g. a resend already rotated the envelope before the timer fired — not this mock's job
        // to surface that as an error.
        config.apply(new EsignProviderConfig(EsignMode.INSTANT, 0, AutoOutcome.SIGN, null));
        doThrow(new IllegalStateException("stale envelope"))
                .when(signatureEvents).apply(any(), any());

        client.registerEnvelope("app-6");
        Runnable scheduledTask = captureScheduledTask();

        scheduledTask.run(); // must not throw
        verify(signatureEvents, times(1)).apply(any(), any());
    }

    @Test
    void demoExpirySecondsOverridesTheDefaultWindowForTheNextEnvelopeOnly() {
        config.apply(new EsignProviderConfig(EsignMode.SILENT, 0, AutoOutcome.SIGN, 30));

        var registration = client.registerEnvelope("app-7");

        assertThat(registration.expirySeconds()).isEqualTo(30);
    }

    @Test
    void withNoDemoExpirySecondsSetTheDefaultFiveDayWindowIsUsed() {
        config.apply(new EsignProviderConfig(EsignMode.SILENT, 0, AutoOutcome.SIGN, null));

        var registration = client.registerEnvelope("app-8");

        assertThat(registration.expirySeconds()).isEqualTo(InMemoryEsignMockClient.DEFAULT_EXPIRY_SECONDS);
    }

    @Test
    void everyRegisteredEnvelopeIdIsUnique() {
        config.apply(new EsignProviderConfig(EsignMode.SILENT, 0, AutoOutcome.SIGN, null));

        var a = client.registerEnvelope("app-9");
        var b = client.registerEnvelope("app-9");

        assertThat(a.envelopeId()).isNotEqualTo(b.envelopeId());
    }

    private Runnable captureScheduledTask() {
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler, times(1)).schedule(task.capture(), any(Instant.class));
        return task.getValue();
    }
}
