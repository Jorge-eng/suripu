package com.hello.suripu.core.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Optional;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

public class TimelineFeedback {

    @JsonProperty("date_of_night")
    public final DateTime dateOfNight;

    @JsonProperty("old_time_event")
    public final String oldTimeOfEvent;

    @JsonProperty("new_time_event")
    public final String newTimeOfEvent;

    @JsonProperty("event_type")
    public final Event.Type eventType;

    @JsonProperty("account_id")
    public final Optional<Long> accountId;

    @JsonProperty("created")
    public final Optional<Long> created;

    @JsonProperty("id")
    public final Optional<Long> id;

    //public for testing purposes
    public TimelineFeedback(final DateTime dateOfNight, final String oldTimeOfEvent, final String newTimeOfEvent, final Event.Type eventType, final Optional<Long> accountId, final Optional<Long> created, final Long id) {
        this.dateOfNight= dateOfNight;
        this.oldTimeOfEvent = oldTimeOfEvent;
        this.newTimeOfEvent = newTimeOfEvent;
        this.eventType = eventType;
        this.accountId = accountId;
        this.created = created; //when inserting, this happens automatically (ergo you can make this field absent).  When querying, this field will be populated.
        this.id = Optional.fromNullable(id);
    }

    @JsonCreator
    public static TimelineFeedback create(
            @JsonProperty("date_of_night") final String dateOfNight,
            @JsonProperty("old_time_of_event") final String oldTimeOfEvent,
            @JsonProperty("new_time_of_event") final String newTimeOfEvent,
            @JsonProperty("event_type") final String eventTypeString) {

        final DateTime date = DateTime.parse(dateOfNight);
        final DateTime realDate = new DateTime(date.getMillis(), DateTimeZone.UTC).withTimeAtStartOfDay();
        final Event.Type eventType = Event.Type.fromString(eventTypeString);
        return new TimelineFeedback(realDate, oldTimeOfEvent, newTimeOfEvent, eventType, Optional.<Long>absent(),Optional.<Long>absent(), null);
    }

    public static TimelineFeedback create(final DateTime dateOfNight, final String oldTimeOfEvent, final String newTimeOfEvent, final Event.Type eventType) {
        return new TimelineFeedback(dateOfNight,oldTimeOfEvent,newTimeOfEvent,eventType,Optional.<Long>absent(),Optional.<Long>absent(), null);
    }

    public static TimelineFeedback create(final DateTime dateOfNight, final String oldTimeOfEvent, final String newTimeOfEvent, final Event.Type eventType, final Long accountId, final Long created) {
        return new TimelineFeedback(dateOfNight,oldTimeOfEvent,newTimeOfEvent,eventType,Optional.of(accountId),Optional.of(created), null);
    }

    public static TimelineFeedback create(final String dateOfNight, final String oldTimeOfEvent, final String newTimeOfEvent, final Event.Type eventType, final Long accountId) {
        final DateTime realDate = new DateTime(DateTime.parse(dateOfNight), DateTimeZone.UTC).withTimeAtStartOfDay();
        return new TimelineFeedback(realDate, oldTimeOfEvent,newTimeOfEvent,eventType,Optional.of(accountId),Optional.<Long>absent(), null);
    }

    public static TimelineFeedback create(final DateTime dateOfNight, final String oldTimeOfEvent, final String newTimeOfEvent, final Event.Type eventType, final Long accountId, final Long created, final Long id) {
        return new TimelineFeedback(dateOfNight,oldTimeOfEvent,newTimeOfEvent,eventType,Optional.of(accountId),Optional.of(created), id);
    }
}
