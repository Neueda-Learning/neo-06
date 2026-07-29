package com.neobank.module.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.neobank.module.dto.EsignConfigUpdate;
import com.neobank.module.dto.EsignConfigView;
import com.neobank.module.dto.SignatureEventRequest;
import com.neobank.module.model.EsignMode;
import com.neobank.module.model.EsignOutcome;
import com.neobank.module.model.EsignProviderConfig;
import com.neobank.module.model.SignatureEventType;
import com.neobank.module.repository.EsignProviderConfigRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * UC 07's own tests (see the brief's Tests section: "Mock's own tests: mode behaviours, delay
 * honoured, decline path, silent posts nothing").
 *
 * <p>{@link EsignEventScheduler} is faked as "run it right now" so DELAYED mode's behaviour is
 * asserted without an actual sleep \u2014 the delay VALUE requested is what is checked, not real time
 * passing.</p>
 */
class EsignMockServiceTest {

    private EsignProviderConfigRepository configs;
    private SignatureEventService signatureEvents;
    private RecordingScheduler scheduler;
    private EsignMockService service;

    @BeforeEach
    void setUp() {
        configs = mock(EsignProviderConfigRepository.class);
        signatureEvents = mock(SignatureEventService.class);
        scheduler = new RecordingScheduler();
        service = new EsignMockService(configs, signatureEvents, scheduler);
        when(configs.save(any(EsignProviderConfig.class))).thenAnswer(call -> call.getArgument(0));
    }

    private void seed(EsignMode mode, int delaySeconds, EsignOutcome autoOutcome,
            Integer demoExpirySeconds) {
        when(configs.findById(EsignProviderConfig.SINGLETON_ID)).thenReturn(Optional.of(
                new EsignProviderConfig(mode, delaySeconds, autoOutcome, demoExpirySeconds)));
    }

    @Test
    void getConfigSeedsAFreshRowWhenNoneExistsYet() {
        when(configs.findById(EsignProviderConfig.SINGLETON_ID)).thenReturn(Optional.empty());

        EsignConfigView view = service.getConfig();

        assertThat(view).isEqualTo(new EsignConfigView(EsignMode.INSTANT, 0, EsignOutcome.SIGN, null));
        verify(configs).save(any(EsignProviderConfig.class));
    }

    @Test
    void updateConfigAppliesOnlyTheFieldsProvided() {
        // The brief's own PUT example: {"mode":"SILENT","demoExpirySeconds":30} \u2014 delaySeconds and
        // autoOutcome are left as they were.
        seed(EsignMode.INSTANT, 5, EsignOutcome.SIGN, null);

        EsignConfigView updated = service.updateConfig(
                new EsignConfigUpdate(EsignMode.SILENT, null, null, 30));

        assertThat(updated).isEqualTo(new EsignConfigView(EsignMode.SILENT, 5, EsignOutcome.SIGN, 30));
    }

    @Test
    void registerEnvelopeReturnsAFreshIdAndSnapshotsTheCurrentDials() {
        seed(EsignMode.DELAYED, 20, EsignOutcome.DECLINE, null);

        EnvelopeRegistration first = service.registerEnvelope("app-1", "sha1", "Maria Nowak");
        EnvelopeRegistration second = service.registerEnvelope("app-2", "sha2", "Tom Bright");

        assertThat(first.envelopeId()).isNotBlank().isNotEqualTo(second.envelopeId());
        assertThat(first.config()).isEqualTo(new EsignConfigView(EsignMode.DELAYED, 20,
                EsignOutcome.DECLINE, null));
    }

    @Test
    void instantModeFiresInlineWithNoSchedulingHop() {
        EnvelopeRegistration registration = new EnvelopeRegistration("env-1",
                new EsignConfigView(EsignMode.INSTANT, 0, EsignOutcome.SIGN, null));

        service.playAutoMode("app-1", registration);

        // No hop onto the background scheduler — see playAutoMode's javadoc on why INSTANT is
        // fired inline rather than scheduled with a zero delay.
        assertThat(scheduler.scheduleCalls).isZero();
        ArgumentCaptor<SignatureEventRequest> captor = ArgumentCaptor.forClass(SignatureEventRequest.class);
        verify(signatureEvents).apply(org.mockito.ArgumentMatchers.eq("app-1"), captor.capture());
        assertThat(captor.getValue().envelopeId()).isEqualTo("env-1");
        assertThat(captor.getValue().event()).isEqualTo(SignatureEventType.SIGNED);
    }

    @Test
    void delayedModeHonoursTheConfiguredDelayAndPlaysTheAutoOutcome() {
        EnvelopeRegistration registration = new EnvelopeRegistration("env-2",
                new EsignConfigView(EsignMode.DELAYED, 20, EsignOutcome.DECLINE, null));

        service.playAutoMode("app-2", registration);

        assertThat(scheduler.lastDelaySeconds).isEqualTo(20);
        ArgumentCaptor<SignatureEventRequest> captor = ArgumentCaptor.forClass(SignatureEventRequest.class);
        verify(signatureEvents).apply(org.mockito.ArgumentMatchers.eq("app-2"), captor.capture());
        assertThat(captor.getValue().event()).isEqualTo(SignatureEventType.DECLINED);
    }

    @Test
    void silentModePostsNothing() {
        EnvelopeRegistration registration = new EnvelopeRegistration("env-3",
                new EsignConfigView(EsignMode.SILENT, 0, EsignOutcome.SIGN, 30));

        service.playAutoMode("app-3", registration);

        assertThat(scheduler.scheduleCalls).isZero();
        verify(signatureEvents, never()).apply(any(), any());
    }

    @Test
    void aFailureApplyingTheAutoEventIsLoggedAndSwallowedRatherThanCrashingTheSchedulerThread() {
        EnvelopeRegistration registration = new EnvelopeRegistration("env-4",
                new EsignConfigView(EsignMode.INSTANT, 0, EsignOutcome.SIGN, null));
        when(signatureEvents.apply(any(), any()))
                .thenThrow(new IllegalStateException("case already overridden"));

        // Must not propagate — the scheduler runs the task synchronously in this fake, so any
        // exception would otherwise bubble straight out of this test.
        service.playAutoMode("app-4", registration);

        verify(signatureEvents, times(1)).apply(any(), any());
    }

    /** Runs the task immediately, recording the delay it was asked for — no real waiting. */
    private static final class RecordingScheduler implements EsignEventScheduler {
        int scheduleCalls;
        int lastDelaySeconds;

        @Override
        public void schedule(Runnable task, int delaySeconds) {
            scheduleCalls++;
            lastDelaySeconds = delaySeconds;
            task.run();
        }
    }
}
