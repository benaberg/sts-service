const socket = new WebSocket("ws://" + location.hostname + ":{{WS_PORT}}");
var chart = null;
var temperatureData = null;
var lastUpdated = 0;
var currentSensor = null;
const sensors = new Map();

class Sensor {
    constructor(id, name) {
        this.id = id;
        this.name = name;
    }
}

updateSensorList(null);
updateHistoryTabVisibility();

socket.onmessage = (event) => {
    const data = JSON.parse(event.data);
    if (data.log) {
        appendLogMessage(data.log);
        return;
    }
    if (data.sensorId && data.sensorName && !sensors.has(data.sensorName)) {
        sensors.set(data.sensorId, data.sensorName);
        updateSensorList(new Sensor(data.sensorId, data.sensorName));
    }
    if (!currentSensor || data.sensorId != currentSensor.id) {
        return;
    }
    if (data.temperature && data.timestamp) {
        document.getElementById("temperature").textContent = "Current Temperature: " + data.temperature + "°C";
        document.getElementById("last-updated").textContent = "Last Updated: " + formatTimestamp(data.timestamp);
        document.getElementById("last-updated").style.visibility = "visible";

        // Add reading to chart data
        if (temperatureData != null && data.timestamp != lastUpdated) {
            addTemperatureReading(data);
            updateChartTimeRange(Date.now());
        }

        if (data.timestamp != lastUpdated) {
            lastUpdated = data.timestamp;
        }
    }
};

socket.onerror = e => {
    document.getElementById("temperature").textContent = "Connection error: " + e.message;
    document.getElementById("last-updated").style.visibility = "hidden";
};

document.getElementById("logTab").addEventListener("click", () => {
    document.getElementById("logTab").classList.add("active");
    document.getElementById("historyTab").classList.remove("active");
    document.getElementById("timeRangeSelect").classList.add("hidden");
    document.getElementById("log").classList.remove("hidden");
    document.getElementById("history").classList.add("hidden");
});

document.getElementById("sensorSelect").addEventListener("click", (event) => {
    // Ignore select-box clicks
    if (event.target.type == "select-one") {
        return;
    }
    currentSensor = new Sensor(event.target.value, event.target.textContent);
    updateSensorList(currentSensor);
    updateHistoryTabVisibility();
    loadHistoryData();
});

document.getElementById("historyTab").addEventListener("click", () => {
    document.getElementById("historyTab").classList.add("active");
    document.getElementById("timeRangeSelect").classList.remove("hidden");
    document.getElementById("logTab").classList.remove("active");
    document.getElementById("history").classList.remove("hidden");
    document.getElementById("log").classList.add("hidden");
});

const timeRangeSelect = document.getElementById('timeRangeSelect');
timeRangeSelect.addEventListener('change', () => {
    if (chart != null && temperatureData != null) {
        loadHistoryData();
    }
});

function updateChartTimeRange(end) {
    const hours = parseInt(timeRangeSelect.value);
    const filteredData = temperatureData.filter(d => d.timestamp >= end - hours * 3600 * 1000);

    chart.data.labels = filteredData.map(d => new Date(d.timestamp));
    chart.data.datasets[0].data = filteredData.map(d => d.temperature);

    chart.options.scales.x.min = end - hours * 3600 * 1000;
    chart.options.scales.x.max = end;

    chart.update();
}

async function loadHistoryData() {
    if (!currentSensor) {
        return;
    }

    // Destroy chart if initialized
    if (chart) {
        chart.destroy()
    }

    const hours = parseInt(timeRangeSelect.value);
    const end = Date.now();
    const start = end - hours * 60 * 60 * 1000

    const response = await fetch("/temperature/" + currentSensor.id + "?from=" + start +"&to=" + end);
    temperatureData = await response.json();

    // Parse data
    chartData = temperatureData.map(entry => ({
        x: new Date(entry.timestamp),
        y: entry.temperature
    }));

    const ctx = document.getElementById('historyChart').getContext('2d');

    chart = new Chart(ctx, {
        type: 'line',
        data: {
            datasets: [{
                label: 'Temperature',
                data: chartData,
                borderColor: 'rgb(75, 192, 192)',
                tension: 0.1,
                spanGaps: false
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                legend: {
                    display: true,
                    position: 'top',
                    align: 'end',
                    labels: {
                        boxWidth: 12,
                        padding: 10
                    }
                }
            },
            scales: {
                x: {
                    type: 'time',
                    time: {
                        unit: 'minute', // or 'hour', 'day'
                        tooltipFormat: 'HH:mm:ss dd.MM.yyyy',
                        displayFormats: {
                            minute: 'HH:mm',
                            hour: 'HH:mm',
                            day: 'dd.MM'
                        }
                    },
                    ticks: {
                        autoSkip: true,
                        maxTicksLimit: 10
                    },
                    grid: {
                        lineWidth: 2
                    },
                },
                y: {
                    min: 20,
                    max: 120,
                    title: {
                        display: true,
                        text: 'Temperature (°C)'
                    },
                    grid: {
                        lineWidth: 2
                    }
                }
            }
        }
    });
    updateChartTimeRange(Date.now());
}

function addTemperatureReading(reading) {
    temperatureData.push(reading);
    updateChartTimeRange(Date.now());
}

function appendLogMessage(message) {
    const log = document.getElementById("log");

    // Check if current scroll is near bottom
    const nearBottom = log.scrollHeight - log.scrollTop <= log.clientHeight + 10;

    // Append message
    const entry = document.createElement("div");
    entry.className = "log-entry";
    entry.textContent = message;
    log.appendChild(entry);

    // Limit messages to 10000
    while (log.children.length > 10000) {
        log.removeChild(log.firstChild);
    }

    // Auto-scroll
    if (nearBottom) {
        log.scrollTop = log.scrollHeight;
    }
}

function formatTimestamp(timestampMillis) {
    const date = new Date(timestampMillis);
    const pad = (num) => num.toString().padStart(2, '0');
    const hh = pad(date.getHours());
    const MM = pad(date.getMinutes());
    const ss = pad(date.getSeconds());
    const dd = pad(date.getDate());
    const mm = pad(date.getMonth() + 1);
    const yyyy = date.getFullYear();
    return `${hh}:${MM}:${ss} ${dd}.${mm}.${yyyy}`;
}

// Hide history tab if no sensor is selected
function updateHistoryTabVisibility() {
    const historyTabButton = document.getElementById("historyTab");
    historyTab.disabled = !currentSensor;
}

function updateSensorList(sensor) {
    var select = document.getElementById("sensorSelect");

    // Initially append placeholder
    if (sensor == null) {
        select.innerHTML = '<option value="">-- Select a sensor --</option>';
        return;
    }

    // Remove place holder
    if (currentSensor != null) {
        const placeholder = sensorSelect.querySelector('option[value=""]');
        if (placeholder) {
            placeholder.remove();
        }
    }

    // Add missing options
    let exists = false;
    for (let i = 0; i < select.options.length; i++) {
        if (select.options[i].value === sensor.id.toString()) {
            exists = true;
            break;
        }
    }
    if (!exists) {
        const option = document.createElement("option");
        option.value = sensor.id;
        option.textContent = sensor.name;
        select.appendChild(option);
    }
}