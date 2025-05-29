# sts-service
Implementation of the Sauna Temperature Sensor (STS) backend service.

This service provides two HTTP endpoints (default port 9090): 
- <b>/dashboard</b> for a basic dashboard showing the current temperature reading and timestamp
- <b>/temperature</b> for setting the current sauna temperature (PUT) or fetching the current sauna temperature (GET) as JSON
