package com.hello.suripu.algorithm.sleep;

import com.hello.suripu.algorithm.core.AmplitudeData;
import com.hello.suripu.algorithm.utils.ClusterAmplitudeData;
import com.hello.suripu.algorithm.utils.NumericalUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by pangwu on 3/19/15.
 */
public class MotionCluster {

    private static final Logger LOGGER = LoggerFactory.getLogger(MotionCluster.class);
    public static final double DEFAULT_STD_COUNT = 5d;

    public static List<ClusterAmplitudeData> getClusters(final List<AmplitudeData> amplitudeData, final double stdCount, final int windowSizeInDataCount){
        final List<AmplitudeData> window = new ArrayList<>();
        final List<ClusterAmplitudeData> result = new ArrayList<>();
        double mean = 0d;
        double std = 0d;
        for(final AmplitudeData datum:amplitudeData) {
            window.add(datum);
            if(window.size() < windowSizeInDataCount) {
                mean = NumericalUtils.mean(window);
                std = NumericalUtils.std(window, mean);
                result.add(ClusterAmplitudeData.create(datum, false));
                continue;
            }
               final double value = datum.amplitude;
                if(value > mean + stdCount * std) {
                    LOGGER.debug("* val: {} , mean_t: {}, std_t: {}, date: {}",
                            value,
                            mean,
                            std,
                            new DateTime(datum.timestamp, DateTimeZone.forOffsetMillis(datum.offsetMillis)));
                    result.add(ClusterAmplitudeData.create(datum, true));
                }else {
                    LOGGER.debug("val: {}, mean_t: {}, std_t: {}, date: {}",
                            value,
                            mean,
                            std,
                            new DateTime(datum.timestamp, DateTimeZone.forOffsetMillis(datum.offsetMillis)));
                    std = (std + Math.sqrt(Math.pow(value - mean, 2))) / 2d;
                    mean = (mean + value) / 2d;
                    result.add(ClusterAmplitudeData.create(datum, false));
                }
        }

        return result;
    }
}
