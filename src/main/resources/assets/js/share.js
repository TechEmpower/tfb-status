// -----------------------------------------------------------------------------
// Dynamic behavior for the share page.
// -----------------------------------------------------------------------------

(function() {

  function show(element) {
    element.style.display = "block";
  }

  function hide(element) {
    element.style.display = "none";
  }

  const htmlEscapes = {
    "&": "&amp;",
    "<": "&lt;",
    ">": "&gt;",
    "\"": "&quot;",
    "'": "&#x27;",
    "/": "&#x2F;"
  };

  function escapeForHtml(string) {
    return ("" + string).replace(/[&<>"'\/]/g, function(match) {
      return htmlEscapes[match];
    });
  }

  function showSuccess(uploadResponse) {
    const resultsUrlAnchor = document.querySelector(".success-results-json-url");
    resultsUrlAnchor.href = encodeURI(uploadResponse.resultsUrl);
    resultsUrlAnchor.innerHTML = escapeForHtml(uploadResponse.resultsUrl);

    const visualizeResultsUrlAnchor = document.querySelector(".success-visualize-results-url");
    visualizeResultsUrlAnchor.href = encodeURI(uploadResponse.visualizeResultsUrl);
    visualizeResultsUrlAnchor.innerHTML = escapeForHtml(uploadResponse.visualizeResultsUrl);

    hideErrors();
    show(document.querySelector(".success-content"));
  }

  function hideSuccess() {
    hide(document.querySelector(".success-content"));
  }

  function showErrors(errors) {
    const errorsList = document.querySelector(".error-list");
    errorsList.innerHTML = "";

    for (const error of errors) {
      const errorLi = document.createElement("li");
      errorLi.innerHTML = escapeForHtml(error);
      errorsList.appendChild(errorLi);
    }

    hideSuccess();
    show(errorsList);
  }

  function hideErrors() {
    hide(document.querySelector(".error-list"));
  }

  let currentShareType = null;

  function handleShareTypeChange(shareType) {
    currentShareType = shareType;

    const allSections = document.querySelectorAll(".share-type-section");
    const sectionToShow =
        shareType ? document.querySelector(".share-type-section." + shareType) : null;

    // Hide everything
    for (const section of allSections) {
      hide(section);
    }

    // Show the active section if there is one
    if (sectionToShow) {
      show(sectionToShow);
    }
  }

  function attachShareTypeHandler() {
    const radios = document.querySelectorAll("input[name=\"share-type\"]");
    let selectedShareType = null;

    for (const radio of radios) {
      radio.addEventListener("change", function() {
        handleShareTypeChange(radio.value);
      });

      if (radio.checked && !selectedShareType) {
        selectedShareType = radio.value;
      }
    }

    // Initialize based on current state
    handleShareTypeChange(selectedShareType);
  }

  function handleUploadResponse(responsePromise, onSuccess) {
    responsePromise.then(function(response) {
      return response.json().then(function(json) {
        if (response.ok) {
          return json;
        } else {
          throw new Error(json.message);
        }
      });
    }).then(function(json) {
      showSuccess(json);
      onSuccess();
    }).catch(function(e) {
      console.error("Error uploading:", e);
      showErrors([e.message]);
    });
  }

  function handleSubmitPaste() {
    const pasteTextarea = document.querySelector("#paste-results-json");
    const pasteResultsJson = pasteTextarea.value;
    if (!pasteResultsJson || pasteResultsJson.trim().length === 0) {
      showErrors(["JSON is empty"]);
      return;
    }

    handleUploadResponse(
      fetch("/share/upload", {
        method: "POST",
        headers: {
          "Content-Type": "application/json"
        },
        body: pasteResultsJson
      }),
      function() {
        pasteTextarea.value = "";
      }
    );
  }

  function handleSubmitUpload() {
    const fileInput = document.querySelector("#upload-results-json");
    const file = fileInput.files[0];
    if (!file) {
      showErrors(["Select a file"]);
      return;
    }

    handleUploadResponse(
      fetch("/share/upload", {
        method: "POST",
        headers: {
          "Content-Type": "application/json"
        },
        body: file
      }),
      function() {
        fileInput.value = "";
      }
    );
  }

  function attachSubmitHandler() {
    document.querySelector("#share-form").addEventListener("submit", function(event) {
      event.preventDefault();

      if (currentShareType === "paste") {
        handleSubmitPaste();
      } else if (currentShareType === "upload") {
        handleSubmitUpload();
      } else {
        showErrors(["Select a share method"]);
      }
    });
  }

  function handleDomReady() {
    attachShareTypeHandler();
    attachSubmitHandler();
    hideSuccess();
    hideErrors();
  }

  document.addEventListener("DOMContentLoaded", handleDomReady);

})();
