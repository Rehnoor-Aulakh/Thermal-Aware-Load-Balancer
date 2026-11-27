package com.rehnoor.backendserveragent.service;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FanControlServiceTest {

    private static final String CURVE = "55:30,70:45,80:65,85:80,90:100";

    private final List<String> sent = new ArrayList<>();
    private final AtomicLong now = new AtomicLong();

    private FanControlService service(String port) {
        FanSerialLink link = new FanSerialLink(port, 115200) {
            @Override
            public synchronized boolean send(String line) {
                sent.add(line);
                return true;
            }
        };
        return new FanControlService(link, CURVE, 3, 6000, now::get);
    }

    private static String prediction(double temperature) {
        return "{\"type\":\"prediction\",\"predictedTemperature\":" + temperature + "}";
    }

    @Test
    void curveInterpolatesAndClamps() {
        FanCurve curve = FanCurve.parse(CURVE);
        assertEquals(30, curve.speedFor(20));
        assertEquals(55, curve.speedFor(75));
        assertEquals(86, curve.speedFor(86.5));
        assertEquals(100, curve.speedFor(99));
    }

    @Test
    void invalidCurvesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> FanCurve.parse("70:50,60:80"));
        assertThrows(IllegalArgumentException.class, () -> FanCurve.parse("60:80,70:50"));
        assertThrows(IllegalArgumentException.class, () -> FanCurve.parse("60:120"));
        assertThrows(IllegalArgumentException.class, () -> FanCurve.parse("60"));
    }

    @Test
    void higherPredictionRaisesSpeedButLowerPredictionCannotReduceIt() {
        FanControlService fan = service("TEST");
        fan.handleMessage(prediction(94.8));
        assertEquals(100, fan.update(86));

        FanControlService other = service("TEST");
        other.handleMessage(prediction(60));
        assertEquals(83, other.update(85.75));
    }

    @Test
    void slowsDownOnlyAfterTheHysteresisMargin() {
        FanControlService fan = service("TEST");
        assertEquals(65, fan.update(80));
        assertEquals(65, fan.update(78));
        assertEquals(59, fan.update(77));
        assertEquals(65, fan.update(80));
    }

    @Test
    void stalePredictionIsIgnored() {
        FanControlService fan = service("TEST");
        fan.handleMessage(prediction(95));
        now.set(6001);
        assertEquals(45, fan.update(70));
        assertEquals("FAN 45 70.0 -1.0", sent.get(0));
    }

    @Test
    void missingSensorReadingRunsAtFullSpeed() {
        FanControlService fan = service("TEST");
        assertEquals(100, fan.update(-1));
        assertEquals(45, fan.update(70));
    }

    @Test
    void unrelatedOrBrokenMessagesAreIgnored() {
        FanControlService fan = service("TEST");
        fan.handleMessage("not json");
        fan.handleMessage("{\"type\":\"other\",\"predictedTemperature\":95}");
        fan.handleMessage("{\"type\":\"prediction\",\"predictedTemperature\":\"95\"}");
        fan.handleMessage(prediction(900));
        assertEquals(45, fan.update(70));
    }

    @Test
    void disabledWithoutAPort() {
        assertEquals(-1, service("").update(95));
        assertEquals(0, sent.size());
    }
}
