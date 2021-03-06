package com.hello.suripu.core.db;

import com.google.common.base.Optional;
import com.hello.suripu.core.models.Calibration;

import java.util.List;
import java.util.Map;
import java.util.Set;


public interface CalibrationDAO {
    /**
     * Retrieves calibration for given senseId
     * Results are eventually consistent.
     * Do not use for read to update patterns
     * @param senseId String
     * @return Optional<Calibration>
     */
    Optional<Calibration> get(String senseId);


    /**
     * Retrieves calibration for given senseId
     * Should respect consistency semantics
     * @param senseId
     * @return Optional<Calibration>
     */
    Optional<Calibration> getStrict(String senseId);

    /**
     *
     * @param calibration Calibration
     * @return int
     */
    Optional<Boolean> putForce(Calibration calibration);
    Optional<Boolean> put(Calibration calibration);


    /**
     * Retrieves a map of sense Id to calibration object
     * @param senseIds Set<String>
     * @return Map<String, Calibration>
     */
    Map<String, Calibration> getBatch(Set<String> senseIds);
    Map<String, Calibration> getBatchStrict(Set<String> senseIds);

    /**
     * Delete an entry by senseId
     * @param senseId String
     */
     Boolean delete(String senseId);

    /**
     * Put calibration by Batch
     * @param calibrations List<Calibration>
     */

    Map<String, Optional<Boolean>> putBatchForce(List<Calibration> calibrations);
    Map<String, Optional<Boolean>> putBatch(List<Calibration> calibration);
}
