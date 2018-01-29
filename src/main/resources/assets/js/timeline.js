// -----------------------------------------------------------------------------
// Dynamic behavior for the timeline page.
// -----------------------------------------------------------------------------

(() => {
  const d3 = Plotly.d3;
  const svg = d3.select(".chart");

  const drawTimeline = () => {
    const layoutConfig = {
      title: "Framework Timeline",
      xaxis: {
        autorange: true,
        rangeselector: {
          buttons: [
            {
              count: 1,
              label: "1m",
              step: "month",
              stepmode: "backward"
            },
            {
              count: 6,
              label: "6m",
              step: "month",
              stepmode: "backward"
            },
            {
              step: "all"
            }
          ]},
        rangeslider: {},
        type: "date"
      },
      yaxis: {
        title: "RPS",
        rangemode:"tozero"
      }
    };
    const timelineData = d3.csv
                           .parse(d3.select(".data").text())
                           .map(({ time, rps }) => ({
                             time: new Date(parseInt(time)),
                             rps: parseFloat(rps)
                           }))
                           .sort((a, b) => a.time - b.time);
    const lineData = {
      x: timelineData.map((entry) => entry.time),
      y: timelineData.map((entry) => entry.rps),
      type: "scatter"
    };

    svg.selectAll("*").remove();
    Plotly.newPlot(svg.node(), [lineData], layoutConfig);
  };

  const enableFrameworkSelector = () => {
    const frameworkSelector =
        document.querySelector(".frameworkSelector");

    frameworkSelector.addEventListener(
        "change",
        () => {
          const framework = frameworkSelector.value;

          const testType =
              window.location.href.substring(
                  window.location.href.lastIndexOf("/") + 1);

          window.location = "/timeline/"
                          + encodeURIComponent(framework)
                          + "/"
                          + encodeURIComponent(testType);
        });
  };

  const resizeTimeline = () => Plotly.Plots.resize(svg.node());

  // https://remysharp.com/2010/07/21/throttling-function-calls
  function debounce(fn, delay) {
    var timer = null;
    return function () {
      var context = this, args = arguments;
      clearTimeout(timer);
      timer = setTimeout(function () {
        fn.apply(context, args);
      }, delay);
    };
  }

  window.addEventListener("load", enableFrameworkSelector);
  window.addEventListener("load", drawTimeline);
  window.addEventListener("resize", debounce(resizeTimeline, 250));

})();
