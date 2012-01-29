

// Set animation interval period (33 ~= 30fps)
$.fx.interval = 33;

var clockIds = [
  "Period", "Period_small",
  "Jam", "Jam_small",
  "Lineup",
  "Timeout",
  "Intermission"
];
var clockConversions = {
  Period: _timeConversions.msToMinSecNoZero,
  Period_small: _timeConversions.msToMinSecNoZero,
  Jam: _timeConversions.msToMinSecNoZero,
  Lineup: _timeConversions.msToMinSecNoZero,
  Timeout: _timeConversions.msToMinSecNoZero,
  Intermission: _timeConversions.msToMinSecNoZero
};
var animateTime = {
  sponsor: 1000, /* time to animate from one sponsor banner to the next */
  clock: 500, /* clocks animate out then in, so the full transition time is 2x this */
  team: 500, /* show/hide logo/name */
  leadjammerPulse: 1000
};


// Main setup function
$sb(function() {
  setupMainDiv($("#mainDiv")); // This needs to be part of scoreboard framework

  var sbViewOptions = $sbThisPage.$sb("ViewOptions");
  if (_windowFunctions.checkParam("preview", "true"))
    sbViewOptions = $sbThisPage.$sb("PreviewOptions");

  if (_windowFunctions.checkParam("videomuted", "true"))
    $("video").prop({ muted: true, volume: "0.0" }); // Not all browsers support muted
  if (_windowFunctions.checkParam("videocontrols", "true"))
    $("video").prop("controls", true);

  var view = sbViewOptions.$sb("CurrentView");
  view.$sbBindAndRun("content", function(event, value) {
    var showDiv = $("#"+value+"Div");
    if (!showDiv.length)
      showDiv = $("#sbDiv");
    showDiv.children("video").each(function() { this.play(); });
    $("#mainDiv>div.View").not(showDiv).removeClass("Show", 500, function() {
      $(this).children("video").each(function() { this.pause(); });
    });
    showDiv.addClass("Show", 500);
  });

  sbViewOptions.$sb("View(Image).Src").$sbElement("#imageDiv>img");
  sbViewOptions.$sb("View(Video).Src").$sbElement("#videoDiv>video");
  sbViewOptions.$sb("View(CustomHtml).Src").$sbElement("#htmlDiv>iframe");

  setupSponsorBanners();
  setupTeams();
  setupClocks();

  setupBackgrounds();

  sbViewOptions.$sb("SwapTeams").$sbBindAndRun("content", function(event,value) {
    $("#sbDiv>div.Team,#Timeouts>div.Team").toggleClass("SwapTeams", isTrue(value));
  });
});

// FIXME - needs to be a single call from scoreboard.js
function setupMainDiv(div) {
  div.css({ position: "fixed" });
  $.each( [ "bottom", "top", "left", "right" ], function() {
    $("<div>")
      .css( { width: "100%", height: "100%", background: "grey", position: "absolute" } )
      .css(String(this), "100%")
      .appendTo(div);
  });

  _crgUtils.bindAndRun($(window), "resize", function() {
    var aspect4x3 = _windowFunctions.get4x3Dimensions();
    div.css(aspect4x3).css("fontSize", aspect4x3.height);
  });
}

function setupBackgrounds() {
  _crgUtils.bindAndRun($(window), "resize", function() {
    $("div.WhiteBox").each(function() {
      $(this).css("fontSize", $(this).height()+"px");
    });
  });
}


///////////////
// Team control
///////////////

function setupTeams() {
  $("<div>").attr("id", "Timeouts").appendTo("#sbDiv");

  $("<div>").addClass("Name WhiteBox").appendTo("#Timeouts");
  var timeoutsName = $("<div><a>Timeouts</a></div>").addClass("Name TextContainer").appendTo("#Timeouts");
  _autoFit.enableAutoFitText(timeoutsName, { overage: -20 });

  $.each( [ "1", "2" ], function() {
    var team = String(this);
    var sbTeam = $sb("ScoreBoard.Team("+team+")");

    $("<div>").addClass("Team Team"+team+" Number WhiteBox").appendTo("#Timeouts")
      .append($("<div>").addClass("RedBox full"));
    var teamTimeouts = $("<div><a/></div>").addClass("Team Team"+team+" Number TextContainer")
      .appendTo("#Timeouts");
    sbTeam.$sb("Timeouts").$sbElement(teamTimeouts.children("a"), { sbelement: {
      autoFitText: { overage: 15, useMarginBottom: true }
    } }, "Number");

    var teamDiv = $("<div>").addClass("Team Team"+team).appendTo("#sbDiv");
    $("<div>").addClass("TeamAnimationQueue").appendTo(teamDiv);
    $("<div>").addClass("TeamAnimationFlag").appendTo(teamDiv);
    $("<div><a/></div>").addClass("Name TextContainer").appendTo(teamDiv);
    $("<div><div><img/></div></div>").addClass("Logo").appendTo(teamDiv)
      .children("div").addClass("Inner");
    $("<div>").addClass("Score WhiteBox").appendTo(teamDiv);
    $("<div><a/></div>").addClass("Score TextContainer").appendTo(teamDiv);
    $("<div><div><a/></div></div>").addClass("Jammer ClockAnimation").appendTo(teamDiv)
      .children("div").addClass("TextContainer")
      .children("a").addClass("Jammer");
    $("<div><div><a/></div></div>").addClass("Jammer ClockAnimation").appendTo(teamDiv)
      .children("div").addClass("TextContainer")
      .children("a").addClass("Pulse").css("opacity", 0);
    $("<div><div><a/></div></div>").addClass("Lead ClockAnimation").appendTo(teamDiv)
      .children("div").addClass("TextContainer")
      .children("a").addClass("Lead").text("Lead");

    sbTeam.$sb("Name").$sbElement(teamDiv.find("div.Name>a"), { sbelement: { autoFitText: true } }, "Name");
    sbTeam.$sb("Logo").$sbElement(teamDiv.find("div.Logo img"), "Logo");
    sbTeam.$sb("Score").$sbElement(teamDiv.find("div.Score>a"), { sbelement: { autoFitText: { overage: 40 } } }, "Score");
    sbTeam.$sb("Position(Jammer).Name").$sbElement(teamDiv.find("div.Jammer>div>a"), { sbelement: { autoFitText: true } });

    sbTeam.$sb("Name").$sbBindAndRun("content", function(event,value) {
      teamDiv.find("div.Name,div.Logo").toggleClass("NoName", !value, animateTime.team);
    });

// FIXME - This is the Team Name/Logo animation code - should be cleaned up/reduced
    var resizeName = teamDiv.find("div.Name").data("AutoFit");
    sbTeam.$sb("Logo").$sbBindAndRun("content", function(event, newVal, oldVal) {
      var sbLogo = $sb(this);
      teamDiv.children(".TeamAnimationQueue").queue(function(next) {
        if (!!sbLogo.$sbGet() == teamDiv.find("div.TeamAnimationFlag").hasClass("ShowLogo"))
          next();
        else if (sbLogo.$sbGet()) {
          teamDiv.find("div.Name,div.TeamAnimationFlag").addClass("ShowLogo");
          var resizedCss = resizeName();
          teamDiv.find("div.Name").removeClass("ShowLogo"); resizeName();
          teamDiv.find("div.Name>a").animate(resizedCss, animateTime.team, function() {
            teamDiv.find("div.Name").addClass("ShowLogo"); resizeName();
            teamDiv.find("div.Logo").addClass("ShowLogo", animateTime.team, next);
          });
        } else {
          var curVal = teamDiv.find("div.Logo img").attr("src");
          if (!curVal)
            teamDiv.find("div.Logo img").attr("src", oldVal);
          teamDiv.find("div.Logo").removeClass("ShowLogo", animateTime.team, function() {
            if (!curVal)
              teamDiv.find("div.Logo img").attr("src", curVal);
            teamDiv.find("div.Name,div.TeamAnimationFlag").removeClass("ShowLogo");
            var resizedCss = resizeName();
            teamDiv.find("div.Name").addClass("ShowLogo"); resizeName();
            teamDiv.find("div.Name").animate(resizedCss, animateTime.team, function() {
              teamDiv.find("div.Name").removeClass("ShowLogo"); resizeName();
              next();
            });
          });
        }
      });
    });

    sbTeam.$sb("Position(Jammer).Name").$sbBindAndRun("content", function(event, value) {
      teamDiv.find("div.Jammer>div").toggleClass("ShowJammer", !!value, animateTime.clock, "easeInQuart");
      teamDiv.find("div.Lead>div").toggle(!value);
    });
    sbTeam.$sb("LeadJammer").$sbBindAndRun("content", function(event, value) {
      teamDiv.find("div.Lead>div").toggleClass("ShowLead", isTrue(value), animateTime.clock, "easeInQuart");
    });

    var showTimeoutRedBox = function() {
      var ownTimeout = $sb("ScoreBoard.TimeoutOwner").$sbIs(team);
      var timeoutRunning = $sb("ScoreBoard.Clock(Timeout).Running").$sbIsTrue();
      $("#Timeouts>div.WhiteBox.Team"+team+">div.RedBox").toggle(ownTimeout && timeoutRunning);
    };

    var redBoxTriggers = $sb("ScoreBoard.TimeoutOwner").add($sb("ScoreBoard.Clock(Timeout).Running"));
    _crgUtils.bindAndRun(redBoxTriggers, "content", showTimeoutRedBox);

    setupPulsate(
      function() { return sbTeam.$sb("LeadJammer").$sbIsTrue(); },
      teamDiv.find("div.Jammer>div>a.Pulse"),
      animateTime.leadjammerPulse
    );
  });
}

////////////////
// Clock control
////////////////

function setupClocks() {
  $("#sbDiv")
    .append($("<div>").addClass("ClockAnimationQueue"))
    .append($("<div>").addClass("ClockAnimationFlag ClockAnimation"));

  $.each( clockIds, function() {
//FIXME - should be using class instead of global id attr
    var id = String(this);
    var clock = id.replace(/_.*/,"");
    var sbClock = $sb("ScoreBoard.Clock("+clock+")");

    var clockDiv = $("<div>").attr("id", id).addClass("Clock ClockAnimation").appendTo("#sbDiv")
      .append($("<div>").addClass("Name WhiteBox"))
      .append($("<div><a><span/></a></div>").addClass("Name TextContainer"))
      .append($("<div>").addClass("Time WhiteBox"))
      .append($("<div><a><span/></a></div>").addClass("Time TextContainer"));

    sbClock.$sb("Name").$sbElement(clockDiv.find("div.Name>a>span"), "Name");

    sbClock.$sb("Time").$sbElement(clockDiv.find("div.Time>a>span"), {
      sbelement: {
        convert: clockConversions[id],
        autoFitText: { overage: 25 },
        autoFitTextContainer: "div"
      } }, "Time");
  });

  $("#Period,#Jam").find("div.Name>a").append($("<span>").addClass("space").text(" "))
  $("#Period,#Period_small").find("div.Name>a").append($("<span>").addClass("Number"));
  $sb("ScoreBoard.Clock(Period).Number").$sbElement("#Period>div.Name>a>span.Number,#Period_small>div.Name>a>span.Number");
  $("<div><a><span/></a></div>").addClass("Overtime Name TextContainer")
    .insertAfter("#Period>div.Name.TextContainer,#Period_small>div.Name.TextContainer");
  $("#Period>div.Overtime>a>span").text("Overtime");
  $("#Period_small>div.Overtime>a>span").text("OT");
  $("#Jam,#Jam_small").find("div.Name>a").append($("<span>").addClass("Number"));
  $sb("ScoreBoard.Clock(Jam).Number").$sbElement("#Jam>div.Name>a>span.Number,#Jam_small>div.Name>a>span.Number");
  $("#Period_small>div.Name>a>span.Name").replaceWith($("<span>").addClass("Name").text("P"));
  $("#Jam_small>div.Name>a>span.Name").replaceWith($("<span>").addClass("Name").text("J"));
  $("#Lineup>div.Name>a>span.Name").replaceWith($("<span>").addClass("Name").text("Line Up"));
  $("#Timeout>div.Name>a>span.Name").replaceWith($("<span>").addClass("Name").text("Time Out"));
  $("#Jam_small>div.Time").remove();
  $("#Timeout>div.Name.WhiteBox").prepend($("<div>").addClass("RedBox full"));
  $("#Intermission>div.Name>a>span.Name").remove();

  $sb("ScoreBoard.Overtime").$sbBindAndRun("content", function(event, value) {
    if (isTrue(value)) {
      // we don't want this on the animation queue; it should change immediately,
      // since the intermission clock should be displayed now
      $("#Period,#Period_small").addClass("Overtime");
    } else {
      // use 1ms duration so this gets put on the animation queue,
      // which will allow the "Overtime" to slide out before changing back to "Period 2"
      $("#Period,#Period_small").removeClass("Overtime", 1);
    }
  });

// FIXME - this intermission stuff is a mess, can it get fixed up or simplified?!?
  var intermissionAutoFitText = _autoFit.enableAutoFitText("#Intermission>div.Name.TextContainer");
  $sbThisPage.$sbBindAddRemoveEach("Intermission", function(event,node) {
    $("#Intermission>div.Name>a")
      .append($("<span>").addClass("Unofficial "+node.$sbId))
      .append($("<span>").addClass("Name "+node.$sbId))
      .children("span."+node.$sbId).toggle($sb("ScoreBoard.Clock(Intermission).Number").$sbIs(node.$sbId));

    node.$sb("ShowUnofficial").$sbElement("#Intermission>div.Name>a>span.Unofficial."+node.$sbId, { sbelement: {
      boolean: true,
      convert: { "true": "Unofficial ", "false": "" },
      autoFitText: true,
      autoFitTextContainer: "div"
    } });
    node.$sb("Text").$sbElement("#Intermission>div.Name>a>span.Name."+node.$sbId, { sbelement: {
      autoFitText: true,
      autoFitTextContainer: "div"
    } });

    node.$sb("Confirmed").$sbBindAndRun("content", function(event,value) {
      $("#Intermission>div.Name>a>span.Unofficial."+node.$sbId)
        .toggle(!isTrue(value) && $sb("ScoreBoard.Clock(Intermission).Number").$sbIs(node.$sbId));
      intermissionAutoFitText();
    });
    node.$sb("HideClock").$sbBindAndRun("content", function(event,value) {
      if ($sb("ScoreBoard.Clock(Intermission).Number").$sbIs(node.$sbId))
        $("#Intermission>div.Time").toggle(!isTrue(value));    
    });
  }, function(event,node) {
    $("#Intermission>div.Name>a>span."+node.$sbId).remove();
  });
  $sb("ScoreBoard.Clock(Intermission).Number").$sbBindAndRun("content", function(event,value) {
    $("#Intermission>div.Name>a>span")
      .filter(":not(."+value+")").hide().end()
      .filter("."+value).show()
      .filter(".Unofficial").toggle(!$sbThisPage.$sb("Intermission("+value+").Confirmed").$sbIsTrue());
    intermissionAutoFitText();
    $("#Intermission>div.Time").toggle(!$sbThisPage.$sb("Intermission("+value+").HideClock").$sbIsTrue());
  });

  var clockChange = function(event, value, intermission) {
    if (isTrue(value) || intermission)
      $("#sbDiv>div.ClockAnimationQueue").queue(clockRunningChange);
  };
  $sb("ScoreBoard.Clock(Jam).Running").$sbBindAndRun("content", clockChange);
  $sb("ScoreBoard.Clock(Lineup).Running").$sbBindAndRun("content", clockChange);
  $sb("ScoreBoard.Clock(Timeout).Running").$sbBindAndRun("content", clockChange);
  $sb("ScoreBoard.Clock(Intermission).Running").$sbBindAndRun("content", function(event, value) {
    clockChange(event, value, 1);
  });
}

function clockRunningChange(next) {
  if ($sb("ScoreBoard.Clock(Jam).Running").$sbIsTrue())
    showClocks("Jam", next);
  else if ($sb("ScoreBoard.Clock(Timeout).Running").$sbIsTrue())
    showClocks("Timeout", next);
  else if ($sb("ScoreBoard.Clock(Lineup).Running").$sbIsTrue())
    showClocks("Lineup", next);
  else if ($sb("ScoreBoard.Clock(Intermission).Running").$sbIsTrue())
    showClocks("Intermission", next);
  else
    showClocks("Jam", next);
}

var currentClock = "";
function showClocks(clock, next) {
  if (currentClock == clock) {
    next();
    return;
  }
  var toClass = "Show"+clock;
  var transClass = "Trans"+currentClock+clock;
  var fromClasses = "ShowPeriod ShowJam ShowLineup ShowTimeout ShowIntermission".replace(toClass, "");
  if (currentClock)
    $("#sbDiv div.ClockAnimation").switchClass(fromClasses, transClass, animateTime.clock, "easeInQuart");
  $("#sbDiv div.ClockAnimation").switchClass(transClass, toClass, animateTime.clock, "easeInQuart",
    function() { $(this).hasClass("ClockAnimationFlag") && next(); }
  );
  currentClock = clock;
}


/////////////////////////
// Sponsor Banner control
/////////////////////////

function setupSponsorBanners() {
  $("<div>").attr("id", "SponsorBox").addClass("ClockAnimation").appendTo("#sbDiv")
    .append($("<div><img/></div>").addClass("CurrentImg"))
    .append($("<div><img/></div>").addClass("NextImg"));
  var getNextSrc = function(currentSrc) {
      var banners = $.map($sb("Images.Type(sponsor_banner)").find("Image>Src"), function(e) {
        return $sb(e).$sbGet();
      });
      banners.sort();
      var index = $.inArray(currentSrc, banners) + 1;
      if ((0 > index) || (index >= banners.length))
        index = 0;
      return banners[index];
  };
  var firstSrc = getNextSrc();
  $('#SponsorBox>div:first>img').attr("src", firstSrc);
  $('#SponsorBox>div:last>img').attr("src", getNextSrc(firstSrc));
  var nextImgFunction = function() {
    var box = $("#SponsorBox");
    if (box.hasClass("ShowTimeout") || box.hasClass("ShowLineup")) {
      var currentSrc = box.find("div.NextImg>img").attr("src");
      box.children("div.NextImg").switchClass("NextImg", "CurrentImg", animateTime.sponsor);
      box.children("div.CurrentImg").switchClass("CurrentImg", "FinishedImg", animateTime.sponsor, function() {
        $(this).switchClass("FinishedImg", "NextImg", 0).children("img").attr("src", getNextSrc(currentSrc));
      });
    }
    box.delay(5000, "SponsorChangeQueue").queue("SponsorChangeQueue", nextImgFunction).dequeue("SponsorChangeQueue");
  };
  nextImgFunction();
}


////////////
// Animation
////////////

function setupPulsate(pulseCondition, pulseTarget, pulsePeriod) {
  var doPulse = function(next) {
    if (pulseCondition())
      pulseTarget
        .animate({ opacity: 1 }, (pulsePeriod/2), "linear")
        .animate({ opacity: 0 }, (pulsePeriod/2), "linear");
    else
      pulseTarget.delay(500)
    pulseTarget.queue(doPulse);
    next();
  };
  doPulse($.noop);
}

