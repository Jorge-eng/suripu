package com.hello.suripu.app.utils;

import com.hello.suripu.core.models.TrackerMotion;
import javafx.util.Pair;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

import java.util.ArrayList;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Created by pangwu on 11/20/14.
 */
public class TrackerMotionDataSourceTest {
    @Test
    public void testGetMinAmplitude(){
        final ArrayList<TrackerMotion> data = new ArrayList<>();
        final DateTime now = DateTime.now();
        data.add(new TrackerMotion(0L, 0L, 0L, now.getMillis(), -1, DateTimeZone.getDefault().getOffset(now)));  // this one should be skipped
        data.add(new TrackerMotion(0L, 0L, 0L, now.getMillis(), -2, DateTimeZone.getDefault().getOffset(now)));  // -2 & 0xFFFFFFFF
        assertThat(Long.valueOf(TrackerMotionDataSource.getMinAmplitude(data)), is(-2L & 0xFFFFFFFFl));

        data.clear();
        data.add(new TrackerMotion(0L, 0L, 0L, now.getMillis(), -2, DateTimeZone.getDefault().getOffset(now)));  // -2 & 0xFFFFFFFF
        data.add(new TrackerMotion(0L, 0L, 0L, now.getMillis(), 1, DateTimeZone.getDefault().getOffset(now)));  // -2 & 0xFFFFFFFF
        assertThat(TrackerMotionDataSource.getMinAmplitude(data), is(1L));

    }

    @Test
    public void testGetQueryBoundary(){
        final DateTime dayOfNightLocalUTC = new DateTime(2014, 11, 1, 0, 0, DateTimeZone.UTC);
        final Pair<DateTime, DateTime> boundary = TrackerMotionDataSource.getStartEndQueryTimeLocalUTC(dayOfNightLocalUTC, 20, 16);
        assertThat(boundary.getKey(), is(new DateTime(2014, 11, 1, 20, 0, DateTimeZone.UTC)));
        assertThat(boundary.getValue(), is(new DateTime(2014, 11, 2, 16, 0, DateTimeZone.UTC)));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetQueryBoundaryInvalidDayOfNight(){
        final DateTime dayOfNightLocalUTC = new DateTime(2014, 11, 1, 0, 0, DateTimeZone.forOffsetMillis(-255200));
        final Pair<DateTime, DateTime> boundary = TrackerMotionDataSource.getStartEndQueryTimeLocalUTC(dayOfNightLocalUTC, 20, 16);

    }
}
