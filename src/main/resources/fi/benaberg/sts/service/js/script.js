const socket = new WebSocket("ws://" + location.hostname + ":{{WS_PORT}}");

socket.onmessage = (event) => {
    const data = JSON.parse(event.data);
    if (data.temperature && data.timestamp) {
        document.getElementById("temperature").textContent = "Current Temperature: " + data.temperature + "°C";
       document.getElementById("lastUpdated").textContent = "Last Updated: " + formatTimestamp(data.timestamp);
        document.getElementById("lastUpdated").style.visibility = "visible";
    }
    if (data.log) {
        appendLogMessage(data.log);
    }
};
socket.onerror = e => {
    document.getElementById("temperature").textContent = "Connection error: " + e.message;
    document.getElementById("lastUpdated").style.visibility = "hidden";
};

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