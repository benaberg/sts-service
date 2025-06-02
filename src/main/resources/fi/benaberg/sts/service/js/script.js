const socket = new WebSocket("ws://" + location.hostname + ":{{WS_PORT}}");
var chart = null;
var data = null;

socket.onmessage = (event) => {
    const data = JSON.parse(event.data);
    if (data.temperature && data.timestamp) {
        document.getElementById("temperature").textContent = "Current Temperature: " + data.temperature + "°C";
        document.getElementById("last-updated").textContent = "Last Updated: " + formatTimestamp(data.timestamp);
        document.getElementById("last-updated").style.visibility = "visible";
    }
    if (data.log) {
        appendLogMessage(data.log);
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

document.getElementById("historyTab").addEventListener("click", () => {
    document.getElementById("historyTab").classList.add("active");
    document.getElementById("timeRangeSelect").classList.remove("hidden");
    document.getElementById("logTab").classList.remove("active");
        if (!window.historyChartInitialized) {
            loadHistoryData();
            window.historyChartInitialized = true;
        }

    document.getElementById("history").classList.remove("hidden");
    document.getElementById("log").classList.add("hidden");
});

const select = document.getElementById('timeRangeSelect');
select.addEventListener('change', () => {
    if (chart != null && data != null) {
        const hours = parseInt(select.value, 10);
        updateChartTimeRange(hours);
    }
});

function updateChartTimeRange(hours) {
    const now = Date.now();
    const filteredData = data.filter(d => d.timestamp >= now - hours * 3600 * 1000);

    chart.data.labels = filteredData.map(d => new Date(d.timestamp));
    chart.data.datasets[0].data = filteredData.map(d => d.temperature);

    chart.options.scales.x.min = now - hours * 3600 * 1000;
    chart.options.scales.x.max = now;

    chart.update();
}

async function loadHistoryData() {
    const now = Date.now();
    const response = await fetch("/temperature?from=0&to=" + now);
    data = await response.json();

    // Parse data
    chartData = data.map(entry => ({
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
                    beginAtZero: false,
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
    updateChartTimeRange(1);
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