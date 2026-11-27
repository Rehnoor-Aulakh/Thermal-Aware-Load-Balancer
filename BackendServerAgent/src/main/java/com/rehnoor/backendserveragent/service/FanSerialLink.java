package com.rehnoor.backendserveragent.service;

import com.fazecast.jSerialComm.SerialPort;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Writes text lines to the ESP32 over USB serial. The port is opened on demand
 * and reopened after a failure, so the board can be unplugged and reconnected.
 */
@Component
public class FanSerialLink {

    private static final int WRITE_TIMEOUT_MS = 500;

    private final String portName;
    private final int baudRate;

    private SerialPort port;
    private boolean failureReported;

    public FanSerialLink(
            @Value("${fan.serial.port:}") String portName,
            @Value("${fan.serial.baud:115200}") int baudRate
    ) {
        this.portName = portName.trim();
        this.baudRate = baudRate;
    }

    /** Fan control is switched off when no port is configured. */
    public boolean isEnabled() {
        return !portName.isEmpty();
    }

    /** Sends one line. Returns false if it could not be written. */
    public synchronized boolean send(String line) {
        if (!isEnabled()) {
            return false;
        }
        try {
            if (port == null) {
                open();
            }
            discardIncoming();

            byte[] bytes = (line + "\n").getBytes(StandardCharsets.US_ASCII);
            if (port.writeBytes(bytes, bytes.length) != bytes.length) {
                throw new IllegalStateException("write failed");
            }
            failureReported = false;
            return true;
        } catch (Exception exception) {
            // Report once per outage, not on every sample.
            if (!failureReported) {
                System.err.println("Fan controller on " + portName + " is unavailable: "
                        + exception.getMessage() + ". Available ports: " + availablePorts());
                failureReported = true;
            }
            close();
            return false;
        }
    }

    private void open() {
        SerialPort candidate = SerialPort.getCommPort(portName);
        candidate.setComPortParameters(baudRate, 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY);
        candidate.setComPortTimeouts(SerialPort.TIMEOUT_WRITE_BLOCKING, 0, WRITE_TIMEOUT_MS);
        if (!candidate.openPort()) {
            throw new IllegalStateException("could not open the port");
        }
        port = candidate;
        System.out.println("Fan controller connected on " + portName);
    }

    /** The ESP32 prints log messages; drop them so the buffer cannot fill up. */
    private void discardIncoming() {
        int available = port.bytesAvailable();
        if (available < 0) {
            throw new IllegalStateException("the port was disconnected");
        }
        if (available > 0) {
            port.readBytes(new byte[available], available);
        }
    }

    private static String availablePorts() {
        String names = Arrays.stream(SerialPort.getCommPorts())
                .map(SerialPort::getSystemPortName)
                .collect(Collectors.joining(", "));
        return names.isEmpty() ? "none" : names;
    }

    @PreDestroy
    public synchronized void close() {
        if (port != null) {
            port.closePort();
            port = null;
        }
    }
}
