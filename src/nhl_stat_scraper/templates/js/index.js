document.addEventListener("DOMContentLoaded", function(event) {
  var games = document.getElementsByTagName("svg"); //needs to only select svgs in .results

  var bout_toggle = function() {
    var classes = this.getAttribute("class").split(' ');
    var bout = classes.find(function (el) {
      return el.indexOf("bout_") >= 0;
    });
    var bout_games = document.getElementsByClassName(bout);
    for (var i = 0; i < bout_games.length; i++) {
      bout_games[i].classList.toggle("hovered_bout")
    }
  };

  var game_toggle = function() {
    var classes = this.getAttribute("class").split(' ');
    var game = classes.find(function (el) {
      return el.indexOf("game_") >= 0;
    });
    var game_instances = document.getElementsByClassName(game);
    for (var i = 0; i < game_instances.length; i++) {
      game_instances[i].classList.toggle("hovered_game")
    }
  };

  for (var i = 0; i < games.length; i++) {
    games[i].addEventListener('mouseover', bout_toggle, false);
    games[i].addEventListener('mouseout', bout_toggle, false);
    games[i].addEventListener('mouseover', game_toggle, false);
    games[i].addEventListener('mouseout', game_toggle, false);
  }

  // toggles elements that are shown in both the selected and all views
  var hidden_toggle = function(select_our_classes, our_classes, their_classes) {
    if (select_our_classes) {
      for (var i=0; i < their_classes.length; i++) {
        var their_elements = document.getElementsByClassName(their_classes[i]);
        for (var j=0; j < their_elements.length; j++){
          their_elements[j].classList.add("hidden")
        }
      }
      for (var i=0; i < our_classes.length; i++) {
        var our_elements = document.getElementsByClassName(our_classes[i]);
        for (var j=0; j < our_elements.length; j++){
          our_elements[j].classList.remove("hidden")
        }
      }
    } else {
      for (var i=0; i < their_classes.length; i++) {
        var their_elements = document.getElementsByClassName(their_classes[i]);
        for (var j=0; j < their_elements.length; j++){
          their_elements[j].classList.remove("hidden")
        }
      }
    }
  };

  // 3 way toggle - displays our_classes, their_classes, or all_classes
  var exclusive_hidden_toggle = function(select_our_classes,  our_classes, their_classes, all_classes) {
    if (select_our_classes) {
      for (var i=0; i < their_classes.length; i++) {
        var their_elements = document.getElementsByClassName(their_classes[i]);
        for (var j=0; j < their_elements.length; j++){
          their_elements[j].classList.add("hidden")
        }
      }
      for (var i=0; i < all_classes.length; i++) {
        var all_elements = document.getElementsByClassName(all_classes[i]);
        for (var j=0; j < all_elements.length; j++){
          all_elements[j].classList.add("hidden")
        }
      }
      for (var i=0; i < our_classes.length; i++) {
        var our_elements = document.getElementsByClassName(our_classes[i]);
        for (var j=0; j < our_elements.length; j++){
          our_elements[j].classList.remove("hidden")
        }
      }
    } else {
      for (var i=0; i < their_classes.length; i++) {
        var their_elements = document.getElementsByClassName(their_classes[i]);
        for (var j=0; j < their_elements.length; j++){
          their_elements[j].classList.add("hidden")
        }
      }
      for (var i=0; i < our_classes.length; i++) {
        var our_elements = document.getElementsByClassName(our_classes[i]);
        for (var j=0; j < our_elements.length; j++){
          our_elements[j].classList.add("hidden")
        }
      }
      for (var i=0; i < all_classes.length; i++) {
        var all_elements = document.getElementsByClassName(all_classes[i]);
        for (var j=0; j < all_elements.length; j++){
          all_elements[j].classList.remove("hidden")
        }
      }
    }
  }

  var getSiblings = function(el, filter) {
    var siblings = [];
    el = el.parentNode.firstChild;
    do { if (!filter || filter(el)) siblings.push(el); } while (el = el.nextSibling);
    return siblings;
   };

  var hasClassList = function(el) {
    var tn = el.tagName;
    return el.tagName != undefined;
  };

  // function for listener
  var home_away_toggle = function( self, our_classes, their_classes, exc_our_classes, exc_their_classes, exc_all_classes ) {
    var select_our_classes = !(self.classList.contains("selected"));
    hidden_toggle(select_our_classes, our_classes, their_classes);
    exclusive_hidden_toggle(select_our_classes, exc_our_classes, exc_their_classes, exc_all_classes);
    var siblings = getSiblings(self, hasClassList);
    for (var i=0; i < siblings.length; i++) {
      siblings[i].classList.remove("selected");
    };
    if (select_our_classes) {
      self.classList.add("selected");
    };
  };

  var home_select = document.getElementById("home_select");
  var away_select = document.getElementById("away_select");

  var home_classes = ["home_incomplete", "home_win", "home_loss", "home_ot_win", "home_ot_loss", "home_so_win", "home_so_loss", "home_ongoing"];
  var away_classes = ["away_incomplete", "away_win", "away_loss", "away_ot_win", "away_ot_loss", "away_so_win", "away_so_loss", "away_ongoing"];
  var all_classes = [];
  var exc_home_classes = ["home_record", "home_pts"];
  var exc_away_classes = ["away_record", "away_pts"];
  var exc_all_classes = ["record","pts"];

  var home_toggle =  function(){
    var self = this;
    home_away_toggle(self, home_classes, away_classes.concat(all_classes), exc_home_classes, exc_away_classes, exc_all_classes);
  };

  var away_toggle =  function(){
    var self = this;
    home_away_toggle(self, away_classes, home_classes.concat(all_classes), exc_away_classes, exc_home_classes, exc_all_classes);
  };

  home_select.addEventListener('click', home_toggle, false);
  away_select.addEventListener('click', away_toggle, false);

});
