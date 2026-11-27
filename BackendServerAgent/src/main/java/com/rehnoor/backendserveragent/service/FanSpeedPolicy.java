package com.rehnoor.backendserveragent.service;

/**
 * The predictive fan rule.
 *
 * <ul>
 *   <li>The fan follows the higher of the current and predicted temperature, so
 *       a prediction can start cooling early but can never reduce it.</li>
 *   <li>It speeds up immediately and slows down only after the temperature has
 *       dropped by the hysteresis margin, so it does not hunt up and down.</li>
 *   <li>Without a valid CPU reading it runs at full speed.</li>
 * </ul>
 */
final class FanSpeedPolicy {

    static final int FAIL_SAFE_SPEED = 100;

    private final FanCurve curve;
    private final double hysteresis;

    /** The temperature the present fan speed is based on. */
    private double heldTemperature = Double.NEGATIVE_INFINITY;

    FanSpeedPolicy(FanCurve curve, double hysteresis) {
        this.curve = curve;
        this.hysteresis = hysteresis;
    }

    /**
     * @param currentTemperature measured CPU temperature; -1 if unavailable
     * @param predictedTemperature forecast, or null if there is no recent one
     */
    int speedFor(double currentTemperature, Double predictedTemperature) {
        if (!(currentTemperature > 0 && currentTemperature < 110)) {
            heldTemperature = Double.NEGATIVE_INFINITY;
            return FAIL_SAFE_SPEED;
        }

        double effective = predictedTemperature == null
                ? currentTemperature
                : Math.max(currentTemperature, predictedTemperature);

        if (effective >= heldTemperature || effective <= heldTemperature - hysteresis) {
            heldTemperature = effective;
        }
        return curve.speedFor(heldTemperature);
    }
}
