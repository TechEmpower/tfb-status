(function() {
  function show(element) {
    element.style.display = 'block';
  }

  function hide(element) {
    element.style.display = 'none';
  }

  function handleShareTypeChange(shareType) {
    const allSections = document.querySelectorAll('.share-type-section');
    const sectionToShow =
        shareType ? document.querySelector('.share-type-section.' + shareType) : null;

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
    const radios = document.querySelectorAll('input[name="share-type"]');
    let selectedShareType = null;

    for (const radio of radios) {
      radio.addEventListener('change', function() {
        handleShareTypeChange(radio.value);
      });

      if (radio.checked && !selectedShareType) {
        selectedShareType = radio.value;
      }
    }

    // Initialize based on current state
    handleShareTypeChange(selectedShareType);
  }

  function handleDomReady() {
    attachShareTypeHandler();
  }

  document.addEventListener('DOMContentLoaded', handleDomReady);
})();
