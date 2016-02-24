package com.hello.suripu.app.v2;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Optional;
import com.hello.suripu.api.input.State;
import com.hello.suripu.core.db.DeviceDAO;
import com.hello.suripu.core.db.SenseStateDynamoDB;
import com.hello.suripu.core.db.sleep_sounds.DurationDAO;
import com.hello.suripu.core.db.sleep_sounds.SoundDAO;
import com.hello.suripu.core.models.DeviceAccountPair;
import com.hello.suripu.core.models.SenseStateAtTime;
import com.hello.suripu.core.models.sleep_sounds.Duration;
import com.hello.suripu.core.models.sleep_sounds.SleepSoundStatus;
import com.hello.suripu.core.models.sleep_sounds.Sound;
import com.hello.suripu.core.oauth.AccessToken;
import com.hello.suripu.core.oauth.OAuthScope;
import com.hello.suripu.core.oauth.Scope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;

@Path("/v2/sleep_sounds")
public class SleepSoundsResource {
    private static final Logger LOGGER = LoggerFactory.getLogger(SleepSoundsResource.class);

    private final SoundDAO soundDAO;
    private final DurationDAO durationDAO;
    private final SenseStateDynamoDB senseStateDynamoDB;
    private final DeviceDAO deviceDAO;

    private SleepSoundsResource(final SoundDAO soundDAO,
                                final DurationDAO durationDAO,
                                final SenseStateDynamoDB senseStateDynamoDB,
                                final DeviceDAO deviceDAO)
    {
        this.soundDAO = soundDAO;
        this.durationDAO = durationDAO;
        this.senseStateDynamoDB = senseStateDynamoDB;
        this.deviceDAO = deviceDAO;
    }

    public static SleepSoundsResource create(final SoundDAO soundDAO,
                                             final DurationDAO durationDAO,
                                             final SenseStateDynamoDB senseStateDynamoDB,
                                             final DeviceDAO deviceDAO)
    {
        return new SleepSoundsResource(soundDAO, durationDAO, senseStateDynamoDB, deviceDAO);
    }


    //region play
    private static class PlayRequest {

        @JsonProperty("sound")
        public final Long soundId;

        @JsonProperty("duration")
        public final Long durationId;

        private PlayRequest(final Long soundId, final Long durationId) {
            this.soundId = soundId;
            this.durationId = durationId;
        }

        @JsonCreator
        public static PlayRequest create(@JsonProperty("sound") final Long soundId,
                                         @JsonProperty("duration") final Long durationId)
        {
            return new PlayRequest(soundId, durationId);
        }
    }

    @POST
    @Path("/play")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response play(@Scope(OAuthScope.DEVICE_INFORMATION_WRITE) final AccessToken accessToken,
                         @Valid final PlayRequest playRequest)
    {
        final Optional<Sound> soundOptional = soundDAO.getById(playRequest.soundId);
        final Optional<Duration> durationOptional = durationDAO.getById(playRequest.durationId);
        // TODO validate that sense firmware can play sound
        if (!soundOptional.isPresent() || !durationOptional.isPresent()) {
            return Response.status(Response.Status.BAD_REQUEST).entity("").build();
        }
        // TODO send to Messeji!!!
        return Response.status(Response.Status.ACCEPTED).entity("").build();
    }
    //endregion play


    //region stop
    @POST
    @Path("/stop")
    public Response stop(@Scope(OAuthScope.DEVICE_INFORMATION_WRITE) final AccessToken accessToken) {
        // TODO send to Messeji
        return Response.status(Response.Status.ACCEPTED).entity("").build();
    }
    //endregion stop


    //region sounds
    private class SoundResult {
        @JsonProperty("sounds")
        public final List<Sound> sounds;

        public SoundResult(final List<Sound> sounds) {
            this.sounds = sounds;
        }
    }

    @GET
    @Path("/sounds")
    @Produces(MediaType.APPLICATION_JSON)
    // TODO different scope
    public SoundResult getSounds(@Scope(OAuthScope.DEVICE_INFORMATION_READ) final AccessToken accessToken) {
        // TODO map sounds to user's firmware version
        final List<Sound> sounds = soundDAO.all();
        return new SoundResult(sounds);
    }
    //endregion sounds


    //region durations
    private class DurationResult {
        @JsonProperty("durations")
        public final List<Duration> durations;

        public DurationResult(final List<Duration> durations) {
            this.durations= durations;
        }
    }

    @GET
    @Path("/durations")
    @Produces(MediaType.APPLICATION_JSON)
    public DurationResult getDurations(@Scope(OAuthScope.DEVICE_INFORMATION_READ) final AccessToken accessToken) {
        final List<Duration> durations = durationDAO.all();
        return new DurationResult(durations);
    }
    //endregion durations


    //region status
    @GET
    @Path("/status")
    @Produces(MediaType.APPLICATION_JSON)
    public SleepSoundStatus getStatus(@Scope(OAuthScope.DEVICE_INFORMATION_READ) final AccessToken accessToken) {
        final SleepSoundStatus NOT_PLAYING = SleepSoundStatus.create();
        final Long accountId = accessToken.accountId;

        final Optional<DeviceAccountPair> deviceIdPair = deviceDAO.getMostRecentSensePairByAccountId(accountId);
        if (!deviceIdPair.isPresent()) {
            LOGGER.warn("account-id={} device-id-pair=not-found", accountId);
            return NOT_PLAYING;
        }

        final String deviceId = deviceIdPair.get().externalDeviceId;

        final Optional<SenseStateAtTime> senseStateAtTimeOptional = senseStateDynamoDB.getState(deviceId);
        if (!senseStateAtTimeOptional.isPresent()) {
            LOGGER.warn("account-id={} device-id={} sense-state=not-found", accountId, deviceId);
            return NOT_PLAYING;
        }

        final SenseStateAtTime senseStateAtTime = senseStateAtTimeOptional.get();
        if (!senseStateAtTime.state.hasAudioState() || !senseStateAtTime.state.getAudioState().getPlayingAudio()) {
            return NOT_PLAYING;
        }

        final State.AudioState audioState = senseStateAtTime.state.getAudioState();
        if (!(audioState.hasDurationSeconds() && audioState.hasFilePath())) {
            LOGGER.warn("error=inconsistent-sense-state account-id={} device-id={} audio-state-playing={} audio-state-has-duration={} audio-state-has-file-path={}",
                    accountId, deviceId, audioState.getPlayingAudio(), audioState.getDurationSeconds(), audioState.getFilePath());
            return NOT_PLAYING;
        }

        final Optional<Duration> durationOptional = durationDAO.getByDurationSeconds(new Long(audioState.getDurationSeconds()));
        if (!durationOptional.isPresent()) {
            LOGGER.warn("error=duration-not-found account-id={} device-id={} duration-seconds={}",
                    accountId, deviceId, audioState.getDurationSeconds());
            return NOT_PLAYING;
        }

        final Optional<Sound> soundOptional = soundDAO.getByFilePath(audioState.getFilePath());
        if (!soundOptional.isPresent()) {
            LOGGER.warn("error=sound-file-not-found account-id={} device-id={} file-path={}",
                    accountId, deviceId, audioState.getFilePath());
            return NOT_PLAYING;
        }

        return SleepSoundStatus.create(soundOptional.get(), durationOptional.get());
    }
    //endregion status

}