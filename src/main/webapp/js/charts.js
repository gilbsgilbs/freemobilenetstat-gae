// Load the Visualization API.
google.load('visualization', '1', {
    'packages' : [ 'corechart' ]
});
var CHART_OPTIONS = {
    width : 800,
    height : 350,
    pieSliceText: 'none',
    legend: 'labeled',
    chartArea : {
        left : (940 - 500) / 2,
        top : 15,
        width : 500,
        height : "325"
    }
};

// Set a callback to run when the Google Visualization API is loaded.
google.setOnLoadCallback(loadData);

function loadData() {
    $.get("/1/chart/network-usage", drawCharts).error(dataLoadError);
}

function dataLoadError() {
    var networkUsageSpinner = $("#network-usage-spinner");
    networkUsageSpinner.empty();
    networkUsageSpinner.append("Données non disponibles pour le moment");
}

function drawCharts(jsonData) {
    var usersElement = $("#users");
    var users = jsonData["users"];
    var users4g = jsonData["users4g"];
    usersElement.text(users);

    var days = jsonData["days"];
    $("#days").text(days);

    var onOrange = jsonData["orange"];
    var onFreeMobile = jsonData["freeMobile"];
    var onFreeMobile3g = jsonData["freeMobile3g"];
    var onFreeMobile4g = jsonData["freeMobile4g"];
    var onFreeMobileFemtocell = jsonData["freeMobileFemtocell"];

    drawNetworkUsageChart(onOrange, onFreeMobile);
    drawFreeMobileNetworkUsageChart(onFreeMobile3g, onFreeMobile4g, onFreeMobileFemtocell);

    $("#network-usage-spinner").remove();
    $("#network-usage-chart").fadeIn(function() {
        $("#chart-help").slideDown();
    });
    $('.bxslider').show().bxSlider({
        onSlideBefore: function ($slideElement, oldIndex, newIndex) {
            if (newIndex === 0) {
                usersElement.text(users);
            }
            else if (newIndex === 1) {
                usersElement.text(users4g);
            }
        }
    });
}

function drawNetworkUsageChart(onOrange, onFreeMobile) {
    var data = new google.visualization.DataTable();
    data.addColumn("string", "Réseau");
    data.addColumn("number", "Utilisation");

    data.addRows(2);
    data.setCell(0, 0, "Orange");
    data.setCell(0, 1, onOrange, "");
    data.setCell(1, 0, "Free Mobile");
    data.setCell(1, 1, onFreeMobile, "");

    var chart = new google.visualization.PieChart(document.getElementById("network-usage-chart"));
    var options = CHART_OPTIONS;
    options.colors = [ "#FF6600", "#CD1E25" ];
    chart.draw(data, options);
}

function drawFreeMobileNetworkUsageChart(onFreeMobile3g, onFreeMobile4g, onFreeMobileFemtocell) {
    var data = new google.visualization.DataTable();
    data.addColumn("string", "Type de réseau");
    data.addColumn("number", "Utilisation");

    data.addRows(3);
    data.setCell(0, 0, "3G");
    data.setCell(0, 1, onFreeMobile3g, "");
    data.setCell(1, 0, "4G");
    data.setCell(1, 1, onFreeMobile4g, "");
    data.setCell(2, 0, "Femtocell");
    data.setCell(2, 1, onFreeMobileFemtocell, "");

    var chart = new google.visualization.PieChart(document.getElementById("freemobile-network-usage-chart"));
    var options = CHART_OPTIONS;
    options.colors = [ "#CD1E25", "#660F12", "#D2343A" ];
    chart.draw(data, options);
}
