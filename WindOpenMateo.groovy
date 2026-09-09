/*
 *  Wind Sensor (Open-Meteo)
 *
 *  Hubitat driver that reports current wind speed, gusts, and direction from
 *  Open-Meteo forecast data for the hub's configured latitude and longitude.
 *
 *  Author: Jon Wallace
 *  Copyright 2026 Jon Wallace
 *  License: MIT
 *
 *  Notes:
 *  - Wind speed at 10 meters is used.
 *  - Wind direction is reported as degrees and a compass direction.
 *  - Requires the hub location coordinates to be configured in Hubitat.
 */

metadata {
  definition (
    name: "Wind Sensor (Open-Meteo)",
    namespace: "jonw",
    author: "Jon Wallace"
  ) {
    capability "Sensor"
    capability "Refresh"

    attribute "windSpeed", "number"
    attribute "windGust", "number"
    attribute "windDirection", "number"
    attribute "windDirectionCardinal", "string"
    attribute "lastUpdated", "string"
  }

  preferences {
    def lat = location?.latitude
    def lon = location?.longitude
    def hubLocationDescription = (lat != null && lon != null)
      ? "Using hub coordinates: ${lat}, ${lon}"
      : "Hub coordinates are not configured. Set the hub location in Hubitat before this driver can retrieve wind data."

    input name: "hubLocationStatus", type: "paragraph", title: hubLocationDescription, displayDuringSetup: true
    input name: "windSpeedUnit", type: "enum", title: "Wind speed unit", description: "Unit used for wind speed and gust events.", options: ["mph", "km/h", "m/s", "knots"], defaultValue: "mph"
    input name: "updateFrequency", type: "number", title: "Auto-refresh interval (minutes)", description: "How often the driver refreshes wind data.", defaultValue: 15, range: "1..1440"
    input name: "minWindSpeedChange", type: "number", title: "Minimum wind speed change to report", description: "Suppresses wind speed events until the change is at least this amount. Use 0 to report every refresh.", defaultValue: 1, range: "0..500"
    input name: "logEnable", type: "bool", title: "Enable debug logging", description: "Log API requests and update decisions.", defaultValue: false
  }
}

// ===== Lifecycle =====

def installed() {
  log.info "Installed Open-Meteo Wind Sensor"
  initialize()
}

def updated() {
  log.info "Updated settings"
  initialize()
}

def initialize() {
  unschedule()
  scheduleAutoRefresh()
  refreshWindData()
}

// ===== Commands =====

def refresh() {
  refreshWindData()
}

def scheduleAutoRefresh() {
  def minutes = settings?.updateFrequency != null ? settings.updateFrequency as Integer : 15
  minutes = Math.max(1, Math.min(1440, minutes))

  if (logEnable) log.debug "Scheduling auto-refresh every ${minutes} minutes"

  if (minutes == 60) {
    runEvery1Hour(refreshWindData)
  } else if (minutes > 60) {
    runIn(minutes * 60, refreshWindData)
  } else {
    schedule("0 */${minutes} * ? * *", refreshWindData)
  }
}

def refreshWindData() {
  def coords = getHubCoordinates()
  if (!coords) {
    log.error "Hub coordinates are not configured. Set the hub location in Hubitat before retrieving wind data."
    scheduleNextRefresh()
    return
  }

  def lat = coords.lat
  def lon = coords.lon
  def unitLabel = settings?.windSpeedUnit ?: "mph"
  def apiUnit = getOpenMeteoWindSpeedUnit(unitLabel)
  def currentFields = "wind_speed_10m,wind_direction_10m,wind_gusts_10m"
  def url = "https://api.open-meteo.com/v1/forecast?latitude=${lat}&longitude=${lon}&current=${currentFields}&wind_speed_unit=${apiUnit}&timezone=auto"

  if (logEnable) log.debug "Requesting wind data from: ${url}"

  try {
    httpGet([uri: url, contentType: "application/json", timeout: 30]) { resp ->
      if (logEnable) log.debug "Open-Meteo wind response status: ${resp.status}"

      if (resp.status == 200) {
        def current = resp.data?.current

        if (logEnable) {
          log.debug "Open-Meteo wind values: wind_speed_10m=${current?.wind_speed_10m}, wind_gusts_10m=${current?.wind_gusts_10m}, wind_direction_10m=${current?.wind_direction_10m}"
        }

        updateWindSpeed(current?.wind_speed_10m, unitLabel)
        sendOptionalNumberEvent("windGust", current?.wind_gusts_10m, unitLabel, 1)
        updateWindDirection(current?.wind_direction_10m)
        sendEvent(name: "lastUpdated", value: new Date().format("yyyy-MM-dd HH:mm:ss", location.timeZone))
      } else {
        log.error "Failed to get wind data: ${resp.status}"
      }
    }
  } catch (java.net.SocketTimeoutException e) {
    log.warn "Timed out fetching wind data from Open-Meteo; retrying in 60 seconds."
    runIn(60, refreshWindData)
  } catch (Exception e) {
    if (e.message?.toLowerCase()?.contains("timed out")) {
      log.warn "Timed out fetching wind data from Open-Meteo; retrying in 60 seconds."
      runIn(60, refreshWindData)
    } else {
      log.error "Error fetching wind data: ${e.message}"
    }
  }

  scheduleNextRefresh()
}

// ===== Updates =====

def updateWindSpeed(windSpeed, unitLabel) {
  if (windSpeed == null) {
    log.warn "Wind speed data missing in Open-Meteo response"
    return
  }

  def windSpeedValue = (windSpeed as BigDecimal).setScale(1, BigDecimal.ROUND_HALF_UP)
  def currentVal = device.currentValue("windSpeed")
  def currentWindSpeed = currentVal != null ? new BigDecimal(currentVal.toString()) : null
  def threshold = settings?.minWindSpeedChange != null ? (settings.minWindSpeedChange as BigDecimal) : new BigDecimal("1")

  if (currentWindSpeed == null || (windSpeedValue - currentWindSpeed).abs() >= threshold) {
    sendEvent(name: "windSpeed", value: windSpeedValue, unit: unitLabel)
    if (logEnable) log.debug "Wind speed updated to ${windSpeedValue} ${unitLabel}"
  } else if (logEnable) {
    log.debug "Wind speed change (${windSpeedValue} ${unitLabel}) within threshold (${threshold} ${unitLabel}); event not sent."
  }
}

def updateWindDirection(windDirection) {
  if (windDirection == null) {
    log.warn "Wind direction data missing in Open-Meteo response"
    return
  }

  def directionValue = (windDirection as BigDecimal).setScale(0, BigDecimal.ROUND_HALF_UP)
  sendEvent(name: "windDirection", value: directionValue, unit: "degrees")
  sendEvent(name: "windDirectionCardinal", value: getCardinalDirection(directionValue))
  if (logEnable) log.debug "Wind direction updated to ${directionValue} degrees"
}

def sendOptionalNumberEvent(name, value, unit, scale) {
  if (value == null) {
    log.warn "${name} data missing in Open-Meteo response"
    return
  }

  def eventValue = (value as BigDecimal).setScale(scale, BigDecimal.ROUND_HALF_UP)
  sendEvent(name: name, value: eventValue, unit: unit)
  if (logEnable) log.debug "${name} updated to ${eventValue} ${unit}"
}

// ===== Helpers =====

def scheduleNextRefresh() {
  def minutes = settings?.updateFrequency != null ? settings.updateFrequency as Integer : 15
  minutes = Math.max(1, Math.min(1440, minutes))

  if (minutes > 60) {
    runIn(minutes * 60, refreshWindData)
  }
}

def getOpenMeteoWindSpeedUnit(unitLabel) {
  if (unitLabel == "km/h") return "kmh"
  if (unitLabel == "m/s") return "ms"
  if (unitLabel == "knots") return "kn"
  return "mph"
}

def getCardinalDirection(degrees) {
  def directions = ["N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"]
  def index = ((degrees + 11.25) / 22.5).setScale(0, BigDecimal.ROUND_FLOOR) as Integer
  return directions[index % 16]
}

def getHubCoordinates() {
  def lat = location?.latitude
  def lon = location?.longitude
  return (lat != null && lon != null) ? [lat: lat, lon: lon] : null
}
