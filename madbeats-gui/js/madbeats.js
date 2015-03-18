(function() {
  'use strict';

  d3.selection.prototype.moveToFront = function () {
    return this.each(function () {
      this.parentNode.appendChild(this);
    });
  };

  $(window).on('resize', function(){
      draw();
  });

  function draw() {
    d3.select("body").select("svg").remove();

    var width = $(window).width() * 0.975,
        height = $(window).height();

    var squareSide = 5,
        detailWidth = 200,
        detailHeight = 110,
        tooltipMargin = 10;

    var scaleCalculator = function () { return width * height * 0.175; };

    var triangle = d3.svg.symbol().type("triangle-up");

    var projection = d3.geo.mercator()
                           .center([-3.66, 40.419008])
                           .scale(scaleCalculator())
                           .translate([width / 2, height / 2]);

    var path = d3.geo.path()
                     .projection(projection);

    var voronoi = d3.geom.voronoi()
                         .x(function(d) { return d.x; })
                         .y(function(d) { return d.y; });

    var svg = d3.select("body").selectAll("svg")
                               .data([0]);

    var randomInRange = function (min, max) {
      return Math.floor(Math.random() * (max - min + 1)) + min;
    };

    var squareRotateIn = function (d) {
      return d3.interpolateString("rotate("+randomInRange(0, 720)+","+(d.x+(squareSide/2))+","+(d.y+(squareSide/2))+")",
        "rotate("+randomInRange(720, 1440)+","+(d.x+(squareSide/2))+","+(d.y+(squareSide/2))+")");
    };

    var tooltipRotateIn = function (d) {
      return d3.interpolateString("rotate("+randomInRange(720, 1440)+","+(d.x+(squareSide/2))+","+(d.y+(squareSide/2))+")",
        "rotate(0,"+(d.x+(squareSide/2))+","+(d.y+(squareSide/2))+")");
    };

    var squareRotateOut = function (d) {
      return d3.interpolateString("rotate("+randomInRange(-720, 0)+","+(d.x+(squareSide/2))+","+(d.y+(squareSide/2))+")",
        "rotate("+randomInRange(-720, -1440)+","+(d.x+(squareSide/2))+","+(d.y+(squareSide/2))+")");
    };

    var updateClock = function(){
      var now = moment(),
        second = now.seconds() * 6,
        minute = now.minutes() * 6 + second / 60,
        hour = ((now.hours() % 12) / 12) * 360 + 90 + minute / 12;

      d3.select('body').select('#clock-hour').style("transform", "rotate(" + hour + "deg)");
      d3.select('body').select('#clock-minute').style("transform", "rotate(" + minute + "deg)");
      d3.select('body').select('#clock-second').style("transform", "rotate(" + second + "deg)");
      d3.select('body').select('#clock-weekday').text(now.format('ddd'));
      d3.select('body').select('#clock-day').text(now.format('DD'));
      d3.select('body').select('#clock-month').text(now.format('MMM'));
      d3.select('body').select('#clock-year').text(now.format('YYYY'));
    };

    var wrap = function (text, width) {
      text.each(function() {
        var text = d3.select(this),
            words = text.text().split(/\s+/).reverse(),
            word,
            line = [],
            lineNumber = 0,
            lineHeight = 1.1, // ems
            y = parseFloat(text.attr("y")) + tooltipMargin,
            x = parseFloat(text.attr("x")) + tooltipMargin,
            dy = parseFloat(text.attr("dy")),
            tspan = text.text(null)
                        .append("tspan")
                        .attr("x", x)
                        .attr("y", y)
                        .attr("dy", dy + "em");
        while (word = words.pop()) {
          line.push(word);
          tspan.text(line.join(" "));
          if (tspan.node().getComputedTextLength() > width) {
            line.pop();
            tspan.text(line.join(" "));
            line = [word];
            tspan = text.append("tspan")
                        .attr("x", x)
                        .attr("y", y)
                        .attr("dy", ++lineNumber * lineHeight + dy + "em")
                        .text(word);
          }
        }
      });
    };

    svg.enter()
       .append("svg");

    svg.attr("width", width)
       .attr("height", height)
       .attr("viewBox", "0 0 " + width + " " + height);

    // Add gradients.
    var defs = svg.selectAll("defs")
                  .data([0]);
    defs.enter()
        .append("defs");

    var trafficClipPath = defs.selectAll("#trafficClip")
                              .data([0]);
    trafficClipPath.enter()
                   .append("clipPath")
                   .attr("id", "trafficClip")
                   .append("path");

    // Add layers
    // Add map layer.
    var map = svg.selectAll(".map")
                 .data([0]);
    map.enter()
       .append("g")
       .attr("class", "map");
    // Add traffic layer.
    var traffic = svg.selectAll(".traffic")
                     .data([0]);
    traffic.enter()
           .append("g")
           .attr("class", "traffic")
           .attr("clip-path", "url(#trafficClip)");

    // Add points layers.
    var containers = svg.selectAll(".container")
                        .data(["twitter", "instagram", "swarm"], function (d) { return d; });
    containers.enter()
              .append("g")
              .attr("class", function (d) {
                return "container " + d;
              });
    var twitter = svg.select(".twitter");
    var instagram = svg.select(".instagram");
    var swarm = svg.select(".swarm");

    var autoTooltips = svg.selectAll(".auto-tooltip")
      .data([0]);
    autoTooltips.enter()
      .append("g")
      .attr("class", "auto-tooltip");

    // Add legend layer.
    var legend = svg.selectAll(".legend")
                    .data([0]);
    var legendHeight = 100,
      legendMargin = 15,
      legendData = [{name: "Tweets published in the last 5 minutes  /  Tweets publicados en los últimos 5 minutos", class: "tweet"},
        {name: "Foursquare checkins in the last 30 minutes  /  Checkins en Foursquare de los últimos 30 minutos", class: "checkin"},
        {name: "Instagram photos in the last 30 minutes  /  Fotografías en Instagram de los últimos 30 minutos", class: "photo"},
        {name: "Current traffic intensity  /  Intensidad del tráfico actual", class: "traffic-cell"}],
      legendItemOffset = (legendHeight - legendMargin) / legendData.length;
    legend.enter()
          .append("g")
          .attr("class", "legend");
    legend.attr("transform", "translate(" + ((width / 2) * 1.15) + "," + (height - (legendHeight)) +")")
      .each(function () {
        d3.select(this)
          .selectAll(".legendBg")
          .data([0])
          .enter()
          .append("rect")
          .attr("class", "legendBg")
          .attr("width", width / 2)
          .attr("height", legendHeight);
        var legendSq = d3.select(this)
          .selectAll(".legendItem")
          .data(legendData);
        var newLegendSq = legendSq.enter()
                                  .append("g")
                                  .attr("class", "legendItem");

        newLegendSq.append("rect")
          .attr("x", legendMargin)
          .attr("y", function (d, i) {
            return legendMargin + (legendItemOffset * i);
          })
          .attr("width", squareSide * 1.5)
          .attr("height", squareSide * 1.5)
          .attr("class", function (d) { return d.class; });
        newLegendSq.append("text")
          .attr("x", legendMargin + (squareSide * 3))
          .attr("y", function (d, i) {
            return legendMargin + (legendItemOffset * i) + (squareSide * 1.5);
          })
          .text(function (d) { return d.name; });
      });
    d3.json("data/madrid.topojson", function (madrid) {
      // Add buildings.
      var b_data = topojson.feature(madrid, madrid.objects.madrid).features;
      var building = map.selectAll(".building")
                        .data(b_data, function (d) { return d.id; });
      building.enter()
              .append("path")
              .attr("class", "building");
      building.attr("d", path);
      d3.json("data/mShape.geojson", function (pms) {
        trafficClipPath.selectAll("path")
                       .attr("d", function () {
                         return path(pms);
                       });
        (function updateClockInterval(){
          updateClock();
          setTimeout(updateClockInterval, 1000);
        })();
        (function update1MinDataInterval(){
          update1MinData();
          setTimeout(update1MinDataInterval, 1000 * 60);
        })();
        (function displayInfoInterval(){
          displayInfo();
          setTimeout(displayInfoInterval, 1000 * 10);
        })();
      })
    });

    var displayInfo = function () {
      setTimeout( function () {
        autoTooltips.selectAll("text")
          .transition()
          .duration(400)
          .style("opacity",0)
          .remove();
        autoTooltips.selectAll("image")
          .transition()
          .duration(400)
          .style("opacity",0)
          .remove();
        autoTooltips.selectAll("rect")
          .transition()
          .duration(500)
          .ease("bounce")
          .attr("width", 0)
          .attr("height", 0)
          .style("opacity",0)
          .remove();
      }, 1000 * 8);
      var type = randomInRange(0, 4);
      var elements;
      if (type < 2) { elements = instagram.selectAll(".photo"); }
      else if (type < 4) { elements = twitter.selectAll(".tweet"); }
      else if (type === 4 ) { elements = swarm.selectAll(".checkin"); }
      var size = elements.size();
      if (size === 0) {
        type = 2;
        elements = twitter.selectAll(".tweet");
        size = elements.size();
      }
      var chosen = randomInRange(0, size);
      elements.filter(function(d, i) { return i === chosen})
        .each(function (d, i) {
          if (type !== 4) {
            d.x = projection([d.location.lng, d.location.lat])[0];
          } else {
            d.x = projection([d.venue.location.lng, d.venue.location.lat])[0];
          }
          if (type !== 4) {
            d.y = projection([d.location.lng, d.location.lat])[1];
          } else {
            d.y = projection([d.venue.location.lng, d.venue.location.lat])[1];
          }
          if (d.x < 0 || d.x > width || d.y < 0 || d.y > height) {
          } else {
            if (type < 2) {instagram.moveToFront();}
            else if (type < 4) {twitter.moveToFront();}
            else if (type === 4) {swarm.moveToFront();}
            autoTooltips.moveToFront();
            autoTooltips.append("rect")
              .attr("x", function () {
                if (type !== 4) {
                  d.x = projection([d.location.lng, d.location.lat])[0];
                } else {
                  d.x = projection([d.venue.location.lng, d.venue.location.lat])[0];
                }
                return d.x;})
              .attr("y", function () {
                if (type !== 4) {
                  d.y = projection([d.location.lng, d.location.lat])[1];
                } else {
                  d.y = projection([d.venue.location.lng, d.venue.location.lat])[1];
                }
                return d.y; })
              .attr("width", 0)
              .attr("height", 0)
              .transition()
              .duration(500)
              .ease("bounce")
              .attr("width", detailWidth)
              .attr("height", type < 2 ? detailHeight * 2.5 : detailHeight)
              .attr("x", d.x < (width/2) ? d.x : d.x - detailWidth)
              .attr("y", d.y < (height/2) ? d.y : d.y - detailHeight)
              .attrTween("transform", function () { return tooltipRotateIn(d); })
              .attr("class", function () {
                if (type < 2) { return "photo info-border"; }
                else if (type < 4) { return "tweet info-border"; }
                else if (type === 4) { return "checkin info-border";}
              });
            if (type > 1) {
              autoTooltips.append("text")
                .attr("x", d.x < (width / 2) ? d.x : d.x - detailWidth)
                .attr("y", d.y < (height / 2) ? d.y : d.y - detailHeight)
                .attr("dy", 1)
                .text(type !== 4 ? d.text : d.venue.name)
                .attr("opacity", 0)
                .transition()
                .duration(600)
                .attr("opacity", 1)
                .call(wrap, detailWidth - (2 * tooltipMargin), tooltipMargin);
              autoTooltips.append("text")
                .attr("class", "author")
                .attr("x", d.x < (width / 2) ? d.x + tooltipMargin : d.x - detailWidth + tooltipMargin)
                .attr("y", d.y < (height / 2) ? d.y + detailHeight - tooltipMargin : d.y - tooltipMargin)
                .text("@" + d.author.screen_name)
                .attr("opacity", 0)
                .transition()
                .duration(600)
                .attr("opacity", 1);
              autoTooltips.append("text")
                .attr("class", "tooltip-date")
                .attr("x", d.x < (width / 2) ? d.x + detailWidth - tooltipMargin : d.x -tooltipMargin)
                .attr("y", d.y < (height / 2) ? d.y + detailHeight - tooltipMargin : d.y - tooltipMargin)
                .text(moment(d.timestamp/1000).format('HH:mm'))
                .attr("opacity", 0)
                .transition()
                .duration(600)
                .attr("opacity", 1);
            } else {
              autoTooltips.append("image")
                .attr("class", "tooltipPhoto")
                .attr("xlink:href", d.photo)
                .attr("x", d.x < (width/2) ? d.x + tooltipMargin : d.x - detailWidth + tooltipMargin)
                .attr("y", d.y < (height/2) ? d.y : d.y - detailHeight)
                .attr("width", (detailWidth - 2 * tooltipMargin) + "px")
                .attr("height",((detailHeight * 3) - (2 * tooltipMargin)) + "px")
                .attr("opacity", 0)
                .transition()
                .duration(600)
                .attr("opacity", 1);
              autoTooltips.append("text")
                .attr("class", "author")
                .attr("x", d.x < (width/2) ? d.x + tooltipMargin : d.x - detailWidth + tooltipMargin)
                .attr("y", d.y < (height/2) ? d.y + (2 * tooltipMargin): d.y - detailHeight + ( 2 * tooltipMargin))
                .text("@" + d.author.screen_name)
                .attr("opacity", 0)
                .transition()
                .duration(600)
                .attr("opacity", 1);
              autoTooltips.append("text")
                .attr("class", "tooltip-date")
                .attr("x", d.x < (width / 2) ? d.x + detailWidth - tooltipMargin : d.x - tooltipMargin)
                .attr("y", d.y < (height/2) ? d.y + (2 * tooltipMargin): d.y - detailHeight + ( 2 * tooltipMargin))
                .text(moment(d.timestamp/1000).format('HH:mm'))
                .attr("opacity", 0)
                .transition()
                .duration(600)
                .attr("opacity", 1);
            }
          }
        });
    };

    var update1MinData = function () {
      queue()
          .defer(d3.json, "http://madbeats-api.outliers.es/twitter")
          .defer(d3.json, "http://madbeats-api.outliers.es/traffic")
          .defer(d3.json, "http://madbeats-api.outliers.es/swarm")
          .defer(d3.json, "http://madbeats-api.outliers.es/instagram")
          .await(ready1Min);
    };

    var ready1Min = function (error, tweets, mps, checkins, photos) {
      // Add tweets.
      var tweet = twitter.selectAll(".tweet")
                         .data(tweets, function (d) { return d.id; });
      tweet.exit()
           .transition()
           .duration(2000)
           .ease("bounce")
           .attrTween("transform", squareRotateOut)
           .attr("width", 0)
           .attr("height", 0)
           .remove();
      tweet.enter()
           .append("rect")
           .attr("class", "tweet")
           .attr("x", function (d) { d.x = projection([d.location.lng, d.location.lat])[0]; return d.x; })
           .attr("y", function (d) { d.y = projection([d.location.lng, d.location.lat])[1]; return d.y; })
           .attr("width", 0)
           .attr("height", 0);
      tweet.transition()
           .duration(2000)
           .ease("bounce")
           .attr("x", function (d) { d.x = projection([d.location.lng, d.location.lat])[0]; return d.x; })
           .attr("y", function (d) { d.y = projection([d.location.lng, d.location.lat])[1]; return d.y; })
           .attrTween("transform", squareRotateIn)
           .attr("width", squareSide)
           .attr("height", squareSide);

      // Add traffic data.
      var trafficOpacityScale = d3.scale.sqrt()
                                      .domain([0, d3.max(mps, function (d) { return d.intensity; })])
                                      .range([0.0, 0.75]);
      mps.forEach(function (d) {
        var coords = projection([d.location.lng, d.location.lat])
        d.x = coords[0];
        d.y = coords[1];
      });
      voronoi(mps).forEach(function (d) { d.point.cell = d; });
      var traffic_cell = traffic.selectAll(".traffic-cell")
                                .data(mps, function (d) { return d.id; });
      traffic_cell.enter()
                  .append("path")
                  .attr("class", "traffic-cell");
      traffic_cell.transition()
                  .duration(1000)
                  .attr("d", function (d) { return ('cell' in d && d.cell.length) ? "M" + d.cell.join("L") + "Z" : null; })
                  .style("fill-opacity", function (d) { return trafficOpacityScale(d.intensity); });

      // Add Swarm checkins.
      var checkin = swarm.selectAll(".checkin")
                         .data(checkins, function (d) { return d.id; });
      checkin.exit()
             .transition()
             .duration(2000)
             .ease("bounce")
             .attrTween("transform", squareRotateOut)
             .attr("width", 0)
             .attr("height", 0)
             .remove();
      checkin.enter()
             .append("rect")
             .attr("class", "checkin")
             .attr("x", function (d) { d.x = projection([d.venue.location.lng, d.venue.location.lat])[0]; return d.x; })
             .attr("y", function (d) { d.y = projection([d.venue.location.lng, d.venue.location.lat])[1]; return d.y; })
             .attr("width", 0)
             .attr("height", 0);
      checkin.transition()
             .duration(2000)
             .ease("bounce")
             .attr("x", function (d) { d.x = projection([d.venue.location.lng, d.venue.location.lat])[0]; return d.x; })
             .attr("y", function (d) { d.y = projection([d.venue.location.lng, d.venue.location.lat])[1]; return d.y; })
             .attrTween("transform", squareRotateIn)
             .attr("width", squareSide)
             .attr("height", squareSide);

      // Add photos..
      var photo = instagram.selectAll(".photo")
        .data(photos, function (d) { return d.id; });
      photo.exit()
        .transition()
        .duration(2000)
        .ease("bounce")
        .attrTween("transform", squareRotateOut)
        .attr("width", 0)
        .attr("height", 0)
        .remove();
      photo.enter()
        .append("rect")
        .attr("class", "photo")
        .attr("x", function (d) { d.x = projection([d.location.lng, d.location.lat])[0]; return d.x; })
        .attr("y", function (d) { d.y = projection([d.location.lng, d.location.lat])[1]; return d.y; })
        .attr("width", 0)
        .attr("height", 0);
      photo.transition()
        .duration(2000)
        .ease("bounce")
        .attr("x", function (d) { d.x = projection([d.location.lng, d.location.lat])[0]; return d.x; })
        .attr("y", function (d) { d.y = projection([d.location.lng, d.location.lat])[1]; return d.y; })
        .attrTween("transform", squareRotateIn)
        .attr("width", squareSide)
        .attr("height", squareSide);
    };
  };

  draw();

}).call(this);
