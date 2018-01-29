// -----------------------------------------------------------------------------
// Dynamic behavior for the attributes page
// -----------------------------------------------------------------------------

(function() {

  function addErrorMessage(text) {
    const errorList = document.querySelector("#errors");
    const errorEntry = document.createElement("li");
    errorEntry.innerHTML = text;
    errorList.appendChild(errorEntry);
  }

  function submitForm() {
    document.querySelector("#errors").innerHTML = "";
    const attributesJson = document.querySelector("#lookup-attributes").value;
    const testsJson = document.querySelector("#lookup-tests").value;
    let lookupJson;
    try {
      const attributes = JSON.parse(attributesJson.trim());
      const tests = JSON.parse(testsJson.trim());
      lookupJson = JSON.stringify({ attributes, tests });
    } catch(e) {
      addErrorMessage(
          "<b>Error: You have malformed JSON in one of the text areas; "
              + "make sure both text areas contain valid json</b>");
      return;
    }
    document.querySelector("#hidden-input").value = lookupJson;
    document.forms.namedItem("attributes-form").submit();
  }

  function handleDomReady() {
    const submitButton = document.querySelector("#submitLookup");
    submitButton.addEventListener("click", submitForm);
  }

  document.addEventListener("DOMContentLoaded", handleDomReady);

})();
