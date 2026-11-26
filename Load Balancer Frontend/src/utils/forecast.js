// A prediction made at time t is a forecast for t + 10 s, so it must be
// compared with (and drawn at) that later time, not the current one.
export const FORECAST_MS = 10_000;

// Largest delay after the target time at which a reading still counts.
const MAX_LATE_MS = 5_000;

const usable = (value) => Number.isFinite(Number(value)) && Number(value) >= 0;

// Java timestamps can carry more than millisecond precision.
const toMillis = (timestamp) =>
  Date.parse(String(timestamp).replace(/(\.\d{3})\d+/, "$1"));

// Both series in chronological order. `logs` is newest first.
export function forecastSeries(logs) {
  const chronological = [...logs].reverse();
  const series = (field, offset) =>
    chronological
      .filter((log) => usable(log[field]))
      .map((log) => ({
        time: toMillis(log.timestamp) + offset,
        value: Number(log[field]),
      }))
      .filter((point) => Number.isFinite(point.time));

  return {
    actual: series("cpuTemperature", 0),
    predicted: series("predictedTemperature", FORECAST_MS),
  };
}

// The most recent forecast whose target time has been reached, together with
// the first temperature read at or after that time. Null until one exists.
export function latestForecastError(logs) {
  const { actual, predicted } = forecastSeries(logs);

  for (let i = predicted.length - 1; i >= 0; i -= 1) {
    const forecast = predicted[i];
    const reading = actual.find((point) => point.time >= forecast.time);

    if (reading && reading.time - forecast.time <= MAX_LATE_MS) {
      return Math.abs(forecast.value - reading.value);
    }
  }

  return null;
}
