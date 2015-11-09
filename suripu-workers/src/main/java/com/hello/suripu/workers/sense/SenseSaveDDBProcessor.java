package com.hello.suripu.workers.sense;

import com.amazonaws.AmazonClientException;
import com.amazonaws.services.kinesis.clientlibrary.exceptions.InvalidStateException;
import com.amazonaws.services.kinesis.clientlibrary.exceptions.ShutdownException;
import com.amazonaws.services.kinesis.clientlibrary.interfaces.IRecordProcessorCheckpointer;
import com.amazonaws.services.kinesis.clientlibrary.types.ShutdownReason;
import com.amazonaws.services.kinesis.model.Record;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hello.suripu.api.input.DataInputProtos;
import com.hello.suripu.core.db.DeviceDataIngestDAO;
import com.hello.suripu.core.db.MergedUserInfoDynamoDB;
import com.hello.suripu.core.models.DeviceData;
import com.hello.suripu.core.models.UserInfo;
import com.hello.suripu.workers.framework.HelloBaseRecordProcessor;
import com.yammer.metrics.Metrics;
import com.yammer.metrics.annotation.Timed;
import com.yammer.metrics.core.Meter;
import com.yammer.metrics.core.Timer;
import com.yammer.metrics.core.TimerContext;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Created by jakepiccolo on 11/4/15.
 */
// WARNING ALL CHANGES HAVE TO REPLICATED TO SenseSaveProcessor
public class SenseSaveDDBProcessor extends HelloBaseRecordProcessor {

    private final static Logger LOGGER = LoggerFactory.getLogger(SenseSaveProcessor.class);

    private final DeviceDataIngestDAO deviceDataDAO;
    private final MergedUserInfoDynamoDB mergedInfoDynamoDB;
    private final Integer maxRecords;

    private final Meter messagesProcessed;
    private final Meter batchSaved;
    private final Meter batchSaveFailures;
    private final Meter clockOutOfSync;
    private final Timer fetchTimezones;
    private final Meter capacity;


    private String shardId = "";

    public SenseSaveDDBProcessor(final MergedUserInfoDynamoDB mergedInfoDynamoDB, final DeviceDataIngestDAO deviceDataDAO, final Integer maxRecords) {
        this.mergedInfoDynamoDB = mergedInfoDynamoDB;
        this.deviceDataDAO = deviceDataDAO;
        this.maxRecords = maxRecords;

        this.messagesProcessed = Metrics.defaultRegistry().newMeter(deviceDataDAO.name(), "messages", "messages-processed", TimeUnit.SECONDS);
        this.batchSaved = Metrics.defaultRegistry().newMeter(deviceDataDAO.name(), "batch", "batch-saved", TimeUnit.SECONDS);
        this.batchSaveFailures = Metrics.defaultRegistry().newMeter(deviceDataDAO.name(), "batch-failure", "batch-save-failure", TimeUnit.SECONDS);
        this.clockOutOfSync = Metrics.defaultRegistry().newMeter(deviceDataDAO.name(), "clock", "clock-out-of-sync", TimeUnit.SECONDS);
        this.fetchTimezones = Metrics.defaultRegistry().newTimer(deviceDataDAO.name(), "fetch-timezones");
        this.capacity = Metrics.defaultRegistry().newMeter(deviceDataDAO.name(), "capacity", "capacity", TimeUnit.SECONDS);
    }

    @Override
    public void initialize(String s) {
        shardId = s;
    }

    @Timed
    @Override
    public void processRecords(List<Record> records, IRecordProcessorCheckpointer iRecordProcessorCheckpointer) {
        final LinkedList<DeviceData> deviceDataList = new LinkedList<>();

        final Map<String, Long> activeSenses = new HashMap<>(records.size());

        final Map<String, DeviceData> lastSeenDeviceData = Maps.newHashMap();

        for(final Record record : records) {
            DataInputProtos.BatchPeriodicDataWorker batchPeriodicDataWorker;
            try {
                batchPeriodicDataWorker = DataInputProtos.BatchPeriodicDataWorker.parseFrom(record.getData().array());
            } catch (InvalidProtocolBufferException e) {
                LOGGER.error("Failed parsing protobuf: {}", e.getMessage());
                LOGGER.error("Moving to next record");
                continue;
            }

            final String deviceName = batchPeriodicDataWorker.getData().getDeviceId();

            final List<Long> accounts = Lists.newArrayList();
            for (final DataInputProtos.AccountMetadata metadata: batchPeriodicDataWorker.getTimezonesList()) {
                accounts.add(metadata.getAccountId());
            }

            final Map<Long, DateTimeZone> timezonesByUser = getTimezonesByUser(deviceName, batchPeriodicDataWorker, accounts);

            if(timezonesByUser.isEmpty()) {
                LOGGER.warn("Device {} is not stored in DynamoDB or doesn't have any accounts linked.", deviceName);
            } else { // track only for sense paired to accounts
                activeSenses.put(deviceName, batchPeriodicDataWorker.getReceivedAt());
            }


            //LOGGER.info("Protobuf message {}", TextFormat.shortDebugString(batchPeriodicDataWorker));

            for(final DataInputProtos.periodic_data periodicData : batchPeriodicDataWorker.getData().getDataList()) {

                final long createdAtTimestamp = batchPeriodicDataWorker.getReceivedAt();
                final DateTime createdAtRounded = new DateTime(createdAtTimestamp, DateTimeZone.UTC);

                final DateTime periodicDataSampleDateTime = SenseProcessorUtils.getSampleTime(createdAtRounded, periodicData, attemptToRecoverSenseReportedTimeStamp(deviceName));

                if(SenseProcessorUtils.isClockOutOfSync(periodicDataSampleDateTime, createdAtRounded)) {
                    LOGGER.error("The clock for device {} is not within reasonable bounds (2h)", batchPeriodicDataWorker.getData().getDeviceId());
                    LOGGER.error("Created time = {}, sample time = {}, now = {}", createdAtRounded, periodicDataSampleDateTime, DateTime.now());
                    clockOutOfSync.mark();
                    continue;
                }

                final Integer firmwareVersion = SenseProcessorUtils.getFirmwareVersion(batchPeriodicDataWorker, periodicData);

                for (final Long accountId: accounts) {
                    if(!timezonesByUser.containsKey(accountId)) {
                        LOGGER.warn("No timezone info for account {} paired with device {}, account may already unpaired with device but merge table not updated.",
                                accountId,
                                deviceName);
                        continue;
                    }

                    final DateTimeZone userTimeZone = timezonesByUser.get(accountId);


                    final DeviceData.Builder builder = new DeviceData.Builder()
                            .withAccountId(accountId)
                            .withExternalDeviceId(deviceName)
                            .withAmbientTemperature(periodicData.getTemperature())
                            .withAmbientAirQualityRaw(periodicData.getDust())
                            .withAmbientDustVariance(periodicData.getDustVariability())
                            .withAmbientDustMin(periodicData.getDustMin())
                            .withAmbientDustMax(periodicData.getDustMax())
                            .withAmbientHumidity(periodicData.getHumidity())
                            .withAmbientLight(periodicData.getLight())
                            .withAmbientLightVariance(periodicData.getLightVariability())
                            .withAmbientLightPeakiness(periodicData.getLightTonality())
                            .withOffsetMillis(userTimeZone.getOffset(periodicDataSampleDateTime))
                            .withDateTimeUTC(periodicDataSampleDateTime)
                            .withFirmwareVersion(firmwareVersion)
                            .withWaveCount(periodicData.hasWaveCount() ? periodicData.getWaveCount() : 0)
                            .withHoldCount(periodicData.hasHoldCount() ? periodicData.getHoldCount() : 0)
                            .withAudioNumDisturbances(periodicData.hasAudioNumDisturbances() ? periodicData.getAudioNumDisturbances() : 0)
                            .withAudioPeakDisturbancesDB(periodicData.hasAudioPeakDisturbanceEnergyDb() ? periodicData.getAudioPeakDisturbanceEnergyDb() : 0)
                            .withAudioPeakBackgroundDB(periodicData.hasAudioPeakBackgroundEnergyDb() ? periodicData.getAudioPeakBackgroundEnergyDb() : 0);

                    final DeviceData deviceData = builder.build();

                    if(hasLastSeenViewDynamoDBEnabled(deviceName)) {
                        lastSeenDeviceData.put(deviceName, deviceData);
                    }

                    deviceDataList.add(deviceData);
                }
            }
        }


        try {
            int inserted = deviceDataDAO.batchInsertAll(deviceDataList);

            if(inserted == deviceDataList.size()) {
                LOGGER.trace("Batch saved {} data to DB", inserted);
            }else{
                LOGGER.warn("Batch save failed, save {} data using itemize insert.", inserted);
            }

            batchSaved.mark(inserted);
            batchSaveFailures.mark(deviceDataList.size() - inserted);
        } catch (Exception exception) {
            LOGGER.error("Error saving data from {} to {}, {} data discarded",
                    deviceDataList.getFirst().dateTimeUTC,
                    deviceDataList.getLast().dateTimeUTC,  // I love linkedlist
                    deviceDataList.size());
        }

        messagesProcessed.mark(records.size());

        try {
            iRecordProcessorCheckpointer.checkpoint();
        } catch (InvalidStateException e) {
            LOGGER.error("checkpoint {}", e.getMessage());
        } catch (ShutdownException e) {
            LOGGER.error("Received shutdown command at checkpoint, bailing. {}", e.getMessage());
        }

        final int batchCapacity = Math.round(records.size() / (float) maxRecords * 100.0f) ;
        LOGGER.info("{} - seen device: {}", shardId, activeSenses.size());
        LOGGER.info("{} - capacity: {}%", shardId, batchCapacity);
        capacity.mark(batchCapacity);
    }

    @Override
    public void shutdown(IRecordProcessorCheckpointer iRecordProcessorCheckpointer, ShutdownReason shutdownReason) {

        LOGGER.warn("SHUTDOWN: {}", shutdownReason.toString());
        if(shutdownReason == ShutdownReason.TERMINATE) {
            LOGGER.warn("Going to checkpoint");
            try {
                iRecordProcessorCheckpointer.checkpoint();
                LOGGER.warn("Checkpointed successfully");
            } catch (InvalidStateException e) {
                LOGGER.error(e.getMessage());
            } catch (ShutdownException e) {
                LOGGER.error(e.getMessage());
            }
        }

    }


    /**
     *
     * @param deviceName
     * @param batchPeriodicDataWorker
     * @return
     */
    public Map<Long, DateTimeZone> getTimezonesByUser(final String deviceName, final DataInputProtos.BatchPeriodicDataWorker batchPeriodicDataWorker, final List<Long> accountsList) {
        final TimerContext context = fetchTimezones.time();
        try {


            final Map<Long, DateTimeZone> map = Maps.newHashMap();
            for (final DataInputProtos.AccountMetadata accountMetadata : batchPeriodicDataWorker.getTimezonesList()) {
                map.put(accountMetadata.getAccountId(), DateTimeZone.forID(accountMetadata.getTimezone()));
            }

            for (final Long accountId : accountsList) {
                if (!map.containsKey(accountId)) {
                    LOGGER.warn("Found account_id {} in account_device_map but not in alarm_info for device_id {}", accountId, deviceName);
                }
            }

            // Kinesis, DynamoDB and Postgres have a consistent view of accounts
            // move on
            if (!map.isEmpty() && map.size() == accountsList.size() && hasKinesisTimezonesEnabled(deviceName)) {
                return map;
            }


            // At this point we need to go to dynamoDB
            LOGGER.warn("Querying dynamoDB. One or several timezones not found in Kinesis message for device_id = {}.", deviceName);

            int retries = 2;
            for (int i = 0; i < retries; i++) {
                try {
                    final List<UserInfo> userInfoList = this.mergedInfoDynamoDB.getInfo(deviceName);
                    for (UserInfo userInfo : userInfoList) {
                        if (userInfo.timeZone.isPresent()) {
                            map.put(userInfo.accountId, userInfo.timeZone.get());
                        }
                    }
                    break;
                } catch (AmazonClientException exception) {
                    LOGGER.error("Failed getting info from DynamoDB for device = {}", deviceName);
                }

                try {
                    LOGGER.warn("Sleeping for 1 sec");
                    Thread.sleep(1000);
                } catch (InterruptedException e1) {
                    LOGGER.warn("Thread sleep interrupted");
                }
                retries++;
            }

            return map;
        } finally {
            context.stop();
        }
    }
}