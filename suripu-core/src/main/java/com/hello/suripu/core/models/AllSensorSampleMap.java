package com.hello.suripu.core.models;

import com.google.common.base.Optional;

import java.util.HashMap;
import java.util.Map;

public class AllSensorSampleMap {

    private final Map<Long, Sample> light;
    private final Map<Long, Sample> sound;
    private final Map<Long, Sample> humidity;
    private final Map<Long, Sample> temperature;
    private final Map<Long, Sample> particulates;

    public AllSensorSampleMap() {
        this.light = new HashMap<>();
        this.sound = new HashMap<>();
        this.humidity = new HashMap<>();
        this.temperature = new HashMap<>();
        this.particulates = new HashMap<>();
    }

    public void addSample(final Long dateTime, final int offsetMillis,
                          final float light,
                          final float sound,
                          final float humidity,
                          final float temperature,
                          final float particulates) {

        this.light.put(dateTime, new Sample(dateTime, light, offsetMillis));
        this.sound.put(dateTime, new Sample(dateTime, sound, offsetMillis));
        this.humidity.put(dateTime, new Sample(dateTime, humidity, offsetMillis));
        this.temperature.put(dateTime, new Sample(dateTime, temperature, offsetMillis));
        this.particulates.put(dateTime, new Sample(dateTime, particulates, offsetMillis));
    }

    public Optional<Map<Long, Sample>> getData(final Sensor sensor) {
        switch (sensor) {
            case LIGHT:
                if (!this.light.isEmpty()) {
                    return Optional.of(this.light);
                }
            case SOUND:
                if (!this.sound.isEmpty()) {
                    return Optional.of(this.sound);
                }
            case HUMIDITY:
                if (!this.humidity.isEmpty()) {
                    return Optional.of(this.humidity);
                }
            case TEMPERATURE:
                if (!this.temperature.isEmpty()) {
                    return Optional.of(this.temperature);
                }
            case PARTICULATES:
                if (!this.particulates.isEmpty()) {
                    return Optional.of(this.particulates);
                }
            default:
                return Optional.absent();
        }
    }


}
