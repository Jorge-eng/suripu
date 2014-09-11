package com.hello.suripu.workers.pillscorer;

import com.amazonaws.services.kinesis.clientlibrary.interfaces.IRecordProcessor;
import com.amazonaws.services.kinesis.clientlibrary.interfaces.IRecordProcessorFactory;
import com.amazonaws.services.kinesis.clientlibrary.lib.worker.KinesisClientLibConfiguration;
import com.hello.suripu.core.db.SleepScoreDAO;

public class PillScoreProcessorFactory implements IRecordProcessorFactory {

    private SleepScoreDAO sleepScoreDAO;
    private int processThreshold;
    private final KinesisClientLibConfiguration configuration;

    public PillScoreProcessorFactory(
            final SleepScoreDAO sleepScoreDAO,
            final int processThreshold,
            final KinesisClientLibConfiguration configuration) {
        this.sleepScoreDAO = sleepScoreDAO;
        this.processThreshold = processThreshold;
        this.configuration = configuration;
    }

    @Override
    public IRecordProcessor createProcessor() {
        return new PillScoreProcessor(sleepScoreDAO, processThreshold);
    }
}
