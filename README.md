# Wind Sensor (Open-Meteo)

Creates a virtual Hubitat wind sensor using Open-Meteo wind data at 10 meters above ground for the hub’s location.

## What it does

- Reports `windSpeed` and `windGust` in mph, km/h, m/s, or knots; mph is the default.
- Reports `windDirection` in degrees and `windDirectionCardinal` as a compass label.
- Refreshes every 15 minutes by default.
- Applies a configurable minimum change to wind-speed events; gust and direction readings are updated separately.

## Installation

1. Open **Drivers Code** in the Hubitat hub interface and create a new driver.
2. Paste [WindOpenMateo.groovy](WindOpenMateo.groovy) (or import the [raw source](https://raw.githubusercontent.com/b69ca/hubitat-windopenmateo/main/WindOpenMateo.groovy)) and save it.
3. Under **Devices**, add a virtual device and select **Wind Sensor (Open-Meteo)** as its driver type.
4. Set the preferences below and save them.
5. Run **Refresh** and inspect the device’s current states.

Configure the hub’s latitude and longitude before use. The hub needs internet access to Open-Meteo; no separate weather hardware or other driver from this collection is required.

## Preferences

| Setting | Default | Purpose |
| --- | --- | --- |
| Wind speed unit | mph | Choices: mph, km/h, m/s, knots. Unit used for wind speed and gust events. |
| Auto-refresh interval (minutes) | 15 | How often the driver refreshes wind data. Range: 1..1440. |
| Minimum wind speed change to report | 1 | Suppresses wind speed events until the change is at least this amount. Use 0 to report every refresh. Range: 0..500. |
| Enable debug logging | false | Log API requests and update decisions. |

## Usage and behavior

Use `windSpeed` or `windGust` in a custom-attribute notification rule, or display the speed and compass direction together. These readings come from the weather service rather than an anemometer installed at your property.

## Device interface

Capabilities: `Sensor`, `Refresh`.

Additional attributes: `windSpeed`, `windGust`, `windDirection`, `windDirectionCardinal`, `lastUpdated`.

## Troubleshooting

- Check the configured coordinates and the hub’s internet connection if values are missing.
- Enable debug logging and inspect Hubitat Logs for API errors or missing fields.
- A successful refresh may not emit a new primary reading when its change is below the configured threshold.

Data is supplied by [Open-Meteo](https://open-meteo.com/). This project uses the [Forecast API](https://open-meteo.com/en/docs). The source uses public endpoints without an API key. Refer to the provider for data coverage and applicable usage terms.

## Updating

Replace the saved driver code in Hubitat with the latest source and save it. Keep existing devices; there is no need to recreate them. Save preferences and refresh as applicable.

## License

[MIT License](LICENSE). Author: Jon Wallace.
