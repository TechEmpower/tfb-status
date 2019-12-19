// -----------------------------------------------------------------------------
// Dynamic behavior for the home page.
// -----------------------------------------------------------------------------

(function() {

  let updateEventSource;
  let updateWebSocket;

  function enableSse() {
    updateEventSource = new EventSource("/updates");
    updateEventSource.onmessage = handleSseMessage;
    updateEventSource.onerror = handleSseError;
  }

  function disableSse() {
    if (updateEventSource !== undefined) {
      updateEventSource.close();
    }
  }

  function enableWebSockets() {
    const protocol = (location.protocol === "https:") ? "wss:" : "ws:";
    const uri = protocol + "//" + location.host + "/updates";
    updateWebSocket = new WebSocket(uri);
    updateWebSocket.onmessage = handleWebSocketMessage;
    updateWebSocket.onclose = handleWebSocketClose;
  }

  function disableWebSockets() {
    if (updateWebSocket !== undefined) {
      updateWebSocket.close();
    }
  }

  function createFragment(html) {
    const template = document.createElement("template");
    template.innerHTML = html;
    return template.content;
  }

  function handleSseMessage(event) {
    const html = event.data;
    handleIncomingHtml(html);
  }

  function handleWebSocketMessage(event) {
    const html = event.data;
    handleIncomingHtml(html);
  }

  function handleIncomingHtml(html) {
    const fragment = createFragment(html);
    const newRow = fragment.querySelector("tr");
    if (newRow === null) {
      console.log("no <tr> in message, skipping...", html);
      return;
    }
    const uuid = newRow.dataset.uuid;
    if (uuid === undefined) {
      console.log("no uuid in message, skipping...", html);
      return;
    }
    const table = document.querySelector(".resultsTable");
    const tbody = table.querySelector("tbody");
    const rows = Array.from(tbody.querySelectorAll("tr"));
    if (rows.length === 0) {
      tbody.appendChild(newRow);
      return;
    }
    for (const oldRow of rows) {
      if (oldRow.dataset.uuid === uuid) {
        tbody.replaceChild(newRow, oldRow);
        return;
      }
    }
    // This has a potentially weird interaction with paging (you're looking at
    // page 2, this is an update for results in page 1), but it's probably not
    // worth worrying about in practice.
    tbody.insertBefore(newRow, rows[0]);
  }

  function handleSseError(event) {
    if (updateEventSource.readyState === 2 /* closed, not reconnecting */) {
      setTimeout(enableSse, 30000);
    }
  }

  function handleWebSocketClose(event) {
    setTimeout(enableWebSockets, 30000);
  }

  // TODO: After testing this for a while, probably move to web sockets only.
  const useWebSockets = !location.search.includes("useSse");
  if (useWebSockets) {
    window.addEventListener("load", enableWebSockets);
    window.addEventListener("beforeunload", disableWebSockets);
  } else {
    window.addEventListener("load", enableSse);
    window.addEventListener("beforeunload", disableSse);
  }

})();
