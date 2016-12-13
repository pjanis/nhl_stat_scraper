document.addEventListener("DOMContentLoaded", function() {
  var i,j;
  var default_for = function (arg, val) { return typeof arg !== "undefined" ? arg : val; };

  var games = document.querySelectorAll(".results > svg[class*=game_]:not(.game_divider)");

  var game_data = JSON.parse(document.getElementById("game_data").innerHTML);

  var create_index = function(data, index_key) {
    var index = {};
    for(var i=0, data_length= data.length; i < data_length; i++) {
      index[data[i][index_key]] = i;
    }
    return index;
  };

  var game_data_index = create_index(game_data, "game-id");

  var home_row = ["home_win", "home_ot_win"];
  var home_wins = ["home_so_win"].concat(home_row);
  var home_otl = ["home_ot_loss", "home_so_loss"];
  var home_losses = ["home_loss"];
  var away_row = ["away_win", "away_ot_win"];
  var away_wins = ["away_so_win"].concat(away_row);
  var away_otl = ["away_ot_loss", "away_so_loss"];
  var away_losses = ["away_loss"];
  var home_classes = ["home_incomplete", "home_upcoming", "home_ongoing"].concat(home_wins).concat(home_losses).concat(home_otl);
  var away_classes = ["away_incomplete", "away_upcoming", "away_ongoing"].concat(away_wins).concat(away_losses).concat(away_otl);
  var win_classes = home_wins.concat(away_wins);
  var row_wins = home_row.concat(away_row);
  var loss_classes = ["home_loss", "away_loss"];
  var extra_time_loss_classes = ["home_ot_loss","home_so_loss","away_ot_loss","away_so_loss"];
  var all_classes = []; //classes that belong to the complete display (home + away)
  var exc_home_classes = ["home_record", "home_pts"];
  var exc_away_classes = ["away_record", "away_pts"];
  var exc_all_classes = ["record","pts"];

  var append_content = function(base_element, content) {
    if (content === undefined) {
      base_element.innerHTML += "";
    } else if (Array.isArray(content)) {
      for (var i=0; i < content.length; i++) {
        append_content(base_element, content[i]);
      }
    } else if (HTMLCollection.prototype.isPrototypeOf(content)) {
      append_content(base_element, Array.prototype.slice.call(content));
    } else if (hasClassList(content)) {
      base_element.appendChild(content);
    } else if ((typeof content) === "string") {
      base_element.innerHTML += content;
    } else if (content === null) {
      base_element.innerHTML += "";
    } else {
      base_element.innerHTML += String(content);
    }
    return base_element;
  };

  var create_div = function(classes, content) {
    var node = document.createElement("div");
    for (var i = 0; i < classes.length; i++) {
      node.classList.add(classes[i]);
    }
    append_content(node, content);
    return node;
  };

  var clear_div = function(div) {
    var last;
    if (div.tagName === "DIV") {
      while ((last = div.lastChild)) div.removeChild(last);
    }
  };

  var add_game_detail = function(game_detail_data, base_element) {
    var node = create_div(["game_detail_2"], "");
    node.appendChild(create_div(["detail_date"], game_detail_data["game-date"]));
    node.appendChild(create_div(["detail_headline"],
                                create_div(["result_type"],game_detail_data["game-state"])));
    node.appendChild(create_div(["detail"],
                                [create_div(["detail_home"],game_detail_data["home-team-name"]),
                                 create_div(["detail_score"],
                                            [game_detail_data["home-team-score"],
                                             "-",
                                             game_detail_data["away-team-score"]]),
                                 create_div(["detail_away"],game_detail_data["away-team-name"])]));
    base_element.appendChild(node);
  };


  var bout_toggle = function() {
    var classes = this.getAttribute("class").split(" ");
    var bout = classes.find(function (el) {
      return el.indexOf("bout_") >= 0;
    });
    var bout_games = document.getElementsByClassName(bout);
    var i;
    for (i = 0; i < bout_games.length; i++) {
      bout_games[i].classList.toggle("hovered_bout");
    }
  };

  var game_toggle_timer = null;

  var game_toggle = function(self) {
    var classes = self.getAttribute("class").split(" ");
    var game = classes.find(function (el) {
      return el.indexOf("game_") >= 0;
    });
    var game_id = game.split("_")[1];
    var game_instances = document.getElementsByClassName(game);
    var i;
    for (i = 0; i < game_instances.length; i++) {
      game_instances[i].classList.toggle("hovered_game");
    }
    return game_id;
  };

  var update_game_detail = function (game_id) {
    var game_details= document.getElementById("game_details");
    window.clearTimeout(game_toggle_timer);
    game_toggle_timer = window.setTimeout(function(){
      clear_div(game_details);
      add_game_detail(game_data[game_data_index[game_id]],game_details);
    }, 75);
  };

  var remove_game_detail = function () {
    var game_details= document.getElementById("game_details");
    window.clearTimeout(game_toggle_timer);
    game_toggle_timer = window.setTimeout(function(){
      clear_div(game_details);
    }, 75);
  };

  for (i = 0; i < games.length; i++) {
    games[i].addEventListener("mouseenter", bout_toggle, false);
    games[i].addEventListener("mouseleave", bout_toggle, false);
    games[i].addEventListener("mouseenter",
                              function() {
                                var self = this;
                                var game_id = game_toggle(self);
                                update_game_detail(game_id);},
                              false);
    games[i].addEventListener("mouseleave",
                            function() {
                              var self = this;
                              game_toggle(self);
                              remove_game_detail();},
                           false);
  }

  var add_class_to_classes = function(class_name, classes, scoping_element) {
    scoping_element = default_for(scoping_element, document);
    var elements;
    for (var i=0; i < classes.length; i++) {
      elements = scoping_element.getElementsByClassName(classes[i]);
      for (var j=0, el_length= elements.length; j < el_length; j++) {
        elements[j].classList.add(class_name);
      }
    }
  };

  var remove_class_from_classes = function(class_name, classes, scoping_element) {
    scoping_element = default_for(scoping_element, document);
    var elements;
    for (var i=0; i < classes.length; i++) {
      elements = scoping_element.getElementsByClassName(classes[i]);
      for (var j=0, el_length= elements.length; j < el_length; j++) {
        elements[j].classList.remove(class_name);
      }
    }
  };

  // toggles their classes and makes sure our classes are visible when selected
  var hidden_toggle = function(select_our_classes, our_classes, their_classes) {
    if (select_our_classes) {
      add_class_to_classes("hidden", their_classes);
      remove_class_from_classes("hidden", our_classes);
    } else {
      remove_class_from_classes("hidden", their_classes);
    }
  };

  // toggles between our_classes and all_classes while makeing sure their_classes are hidden
  var exclusive_hidden_toggle = function(select_our_classes,  our_classes, their_classes, all_classes) {
    if (select_our_classes) {
      add_class_to_classes("hidden", their_classes);
      add_class_to_classes("hidden", all_classes);
      remove_class_from_classes("hidden", our_classes);
    } else {
      add_class_to_classes("hidden", their_classes);
      add_class_to_classes("hidden", our_classes);
      remove_class_from_classes("hidden", all_classes);
    }
  };

  var getSiblings = function(el, filter) {
    var siblings = [];
    el = el.parentNode.firstChild;
    do { if (!filter || filter(el)) siblings.push(el); } while ((el = el.nextSibling));
    return siblings;
  };

  var hasClassList = function(el) {
    return (el !== null) && (el.tagName != undefined);
  };

  var remove_resort_element = function(scoping_element) {
    scoping_element = default_for(scoping_element, document);
    var i;
    var resort_elements = scoping_element.getElementsByClassName("resort_select");
    for (i=0; i < resort_elements.length; i++) { resort_elements[i].remove(); }
  };

  var count_occurences_of_classes = function(el, classes) {
    var sum=0;
    for (var i=0; i < classes.length; i++) {
      sum += el.getElementsByClassName(classes[i]).length;
    }
    return sum;
  };

  var record = function(results, win_classes, loss_classes, extra_time_loss_classes) {
    return [count_occurences_of_classes(results, win_classes), count_occurences_of_classes(results, loss_classes), count_occurences_of_classes(results, extra_time_loss_classes)].join("-");
  };

  var pts = function(results, win_classes, otl_classes) {
    var sum = 0;
    sum += 2*count_occurences_of_classes(results, win_classes);
    sum += count_occurences_of_classes(results, otl_classes);
    return sum;
  };

  var regulation_overtime_wins = function(results, row_classes) {  //not really needed but here to help clarity
    return count_occurences_of_classes(results, row_classes);
  };

  var record_with_row = function(results, win_classes, loss_classes, extra_time_loss_classes, row_classes) {
    return [(count_occurences_of_classes(results, win_classes) + "(" + regulation_overtime_wins(results, row_classes) + ")"), count_occurences_of_classes(results, loss_classes), count_occurences_of_classes(results, extra_time_loss_classes)].join("-");
  };

  var team_record = function(team) { return record(team.getElementsByClassName("results")[0], win_classes, loss_classes, extra_time_loss_classes); };
  var team_home_record = function(team) { return record(team.getElementsByClassName("results")[0], home_wins, home_losses, home_otl); };
  var team_away_record = function(team) { return record(team.getElementsByClassName("results")[0], away_wins, away_losses, away_otl); };
  var team_record_with_row = function(team) { return record_with_row(team.getElementsByClassName("results")[0], win_classes, loss_classes, extra_time_loss_classes, row_wins); };
  var team_home_record_with_row = function(team) { return record_with_row(team.getElementsByClassName("results")[0], home_wins, home_losses, home_otl, home_row); };
  var team_away_record_with_row = function(team) { return record_with_row(team.getElementsByClassName("results")[0], away_wins, away_losses, away_otl, away_row); };
  var team_pts = function(team) { return pts(team.getElementsByClassName("results")[0], win_classes, extra_time_loss_classes);};
  var team_home_pts = function(team) { return pts(team.getElementsByClassName("results")[0], home_wins, home_otl);};
  var team_away_pts = function(team) { return pts(team.getElementsByClassName("results")[0], away_wins, away_otl);};
  var team_row = function(team) { return regulation_overtime_wins(team.getElementsByClassName("results")[0], row_wins);};
  var team_home_row = function(team) { return regulation_overtime_wins(team.getElementsByClassName("results")[0], home_row );};
  var team_away_row = function(team) { return regulation_overtime_wins(team.getElementsByClassName("results")[0], away_row );};

  var default_sort_functions= [team_pts, team_row];

  var sorted_teams = function(teams, sort_functions) {  //sort_functions are functions to run on team_results to sort
    if (HTMLCollection.prototype.isPrototypeOf(teams)) {
      teams = Array.prototype.slice.call(teams);
    }
    sort_functions = default_for(sort_functions, default_sort_functions);
    return teams.sort( function(a,b) {
      var sort_res_a, sort_res_b;
      for (var i=0; i < sort_functions.length; i++) {
        sort_res_a = sort_functions[i](a);
        sort_res_b = sort_functions[i](b);
        if (sort_res_a > sort_res_b) {
          return -1;
        } else if (sort_res_a < sort_res_b) {
          return 1;
        }
      }
      return -1;
    });
  };

  var resort_teams = function(compare_functions) {
    var teams_elements = document.getElementsByClassName("teams");
    for (i=0; i < teams_elements.length; i++) {
      var teams = Array.prototype.slice.call(teams_elements[i].getElementsByClassName("team"));
      var new_teams = sorted_teams(teams, compare_functions);
      for (j=0; j < teams.length; j++) {
        teams_elements[i].removeChild(teams[j]);
      }
      for (j=0; j < new_teams.length; j++) {
        teams_elements[i].appendChild(new_teams[j]);
      }
    }
  };

  var home_resort= function() { resort_teams([team_home_pts,team_home_row]); };
  var away_resort= function() { resort_teams([team_away_pts,team_away_row]); };
  var last_resort_function=resort_teams;

  var home_away_toggle = function( self, our_classes, their_classes, exc_our_classes, exc_their_classes, exc_all_classes, resort_function ) {
    var select_our_classes = !(self.classList.contains("selected"));
    hidden_toggle(select_our_classes, our_classes, their_classes);
    exclusive_hidden_toggle(select_our_classes, exc_our_classes, exc_their_classes, exc_all_classes);
    var siblings = getSiblings(self, hasClassList);
    for (var i=0; i < siblings.length; i++) {
      siblings[i].classList.remove("selected");
    }
    var resort_div;
    var reset_teams = function() {
      var self = this;
      resort_teams();
      last_resort_function=resort_teams;
      self.remove();
    };
    if (select_our_classes) {
      remove_resort_element(self.parentElement);
      self.classList.add("selected");
      resort_div = create_div(["resort_select"],"Re-Sort");
      self.parentElement.appendChild(resort_div);
      resort_div.addEventListener("click",resort_function,false);
    } else {
      remove_resort_element(self.parentElement);
      if (last_resort_function != resort_teams) {
        resort_div = create_div(["resort_select"],"Re-Sort");
        self.parentElement.appendChild(resort_div);
        resort_div.addEventListener("click",reset_teams,false);
      }
    }
  };

  var home_select = document.getElementById("home_select");
  var away_select = document.getElementById("away_select");

  var home_toggle =  function(){
    var self = this;
    var resort_function = function() {
      var self = this;
      home_resort();
      last_resort_function=home_resort;
      self.remove();
    };
    home_away_toggle(self, home_classes, away_classes.concat(all_classes), exc_home_classes, exc_away_classes, exc_all_classes, resort_function);
  };

  var away_toggle =  function(){
    var self = this;
    var resort_function = function() {
      var self = this;
      away_resort();
      last_resort_function=away_resort;
      self.remove();
    };
    home_away_toggle(self, away_classes, home_classes.concat(all_classes), exc_away_classes, exc_home_classes, exc_all_classes,resort_function);
  };

  home_select.addEventListener("click", home_toggle, false);
  away_select.addEventListener("click", away_toggle, false);

  var row_toggle = function() {
    var i;
    var record_function;
    var home_record_function;
    var away_record_function;
    var selector_element = this;
    if ( selector_element.classList.contains("selected")) {
      record_function = team_record;
      home_record_function = team_home_record;
      away_record_function = team_away_record;
    } else {
      record_function = team_record_with_row;
      home_record_function = team_home_record_with_row;
      away_record_function = team_away_record_with_row;
    }
    selector_element.classList.toggle("selected");

    var teams = document.getElementsByClassName("team");
    var record_element;
    for (i=0; i < teams.length; i++) {
      record_element = teams[i].getElementsByClassName("record")[0];
      record_element.innerHTML = record_function(teams[i]);
      record_element.classList.toggle("record_with_row");

      record_element = teams[i].getElementsByClassName("home_record")[0];
      record_element.innerHTML = home_record_function(teams[i]);
      record_element.classList.toggle("record_with_row");

      record_element = teams[i].getElementsByClassName("away_record")[0];
      record_element.innerHTML = away_record_function(teams[i]);
      record_element.classList.toggle("record_with_row");
    }
  };

  document.getElementById("row_select").addEventListener("click", row_toggle, false);

  var conference_elements = document.getElementsByClassName("conference");
  var conference_name;
  var conference_divisions = {};
  var division_elements;
  for (i=0; i < conference_elements.length; i++) {
    conference_name = conference_elements[i].getElementsByClassName("name")[0].innerHTML;
    division_elements = conference_elements[i].getElementsByClassName("division");
    conference_divisions[conference_name] = [];
    for (j=0; j < division_elements.length; j++) {
      conference_divisions[conference_name].push(division_elements[j].getElementsByClassName("name")[0].innerHTML);
    }
  }


  var remove_divisions = function () {
    var i,j;
    var conference_elements = document.getElementsByClassName("conference");
    var conference_teams;
    var conference_divisions;
    var divisions_div;
    var new_teams;
    for (i=0; i < conference_elements.length; i++) {
      conference_teams =[];
      conference_divisions = conference_elements[i].getElementsByClassName("division");
      for (j=0; j < conference_divisions.length; j++) {
        conference_teams = conference_teams.concat(Array.prototype.slice.call(conference_divisions[j].getElementsByClassName("team")));
      }
      divisions_div = conference_elements[i].getElementsByClassName("divisions")[0];
      conference_elements[i].removeChild(divisions_div);
      new_teams = create_div(["teams"],conference_teams);
      conference_elements[i].appendChild(new_teams);
    }
    last_resort_function();
  };

  var add_divisions = function () {
    var i,j;
    var conference_elements = document.getElementsByClassName("conference");
    var conference_name;
    var conference_teams;
    var division_names;
    var division_teams;
    var divisions_div;
    for (i=0; i < conference_elements.length; i++) {
      conference_name = conference_elements[i].getElementsByClassName("name")[0].innerHTML;
      conference_teams = conference_elements[i].getElementsByClassName("teams")[0];
      divisions_div = create_div(["divisions"]);
      division_names = conference_divisions[conference_name];
      for (j=0; j < division_names.length; j++) {
        division_teams= Array.prototype.slice.call(conference_teams.getElementsByClassName(division_names[j]));
        divisions_div.appendChild(create_div(["division"], [create_div(["name"],division_names[j]),create_div(["teams"],division_teams)]));
      }
      conference_elements[i].removeChild(conference_teams);
      conference_elements[i].appendChild(divisions_div);
    }
    last_resort_function();
  };

  var add_conferences = function () {
    var i;
    var league_element = document.getElementById("league");
    var conference_names = Object.keys(conference_divisions);
    var conference_teams;
    var conference_name;
    var conferences_div = document.createElement("div");
    conferences_div.id = "conferences";
    for (i=0; i < conference_names.length; i++) {
      conference_name = conference_names[i];
      conference_teams= Array.prototype.slice.call(document.getElementsByClassName("team " + conference_name));
      conferences_div.appendChild(
        create_div(["conference"], [create_div(["name"],conference_name), create_div(["teams"], conference_teams)]));
    }
    league_element.removeChild(league_element.getElementsByClassName("name")[0]);
    league_element.removeChild(league_element.getElementsByClassName("teams")[0]);
    league_element.appendChild(conferences_div);
    last_resort_function();
  };


  var add_conferences_divisions = function () {
    var i,j;
    var league_element = document.getElementById("league");
    var conference_names = Object.keys(conference_divisions);
    var conference_element;
    var conference_name;
    var division_names;
    var division_teams;
    var divisions_div;
    var conferences_div = document.createElement("div");
    conferences_div.id = "conferences";
    for (i=0; i < conference_names.length; i++) {
      conference_name = conference_names[i];
      divisions_div = create_div(["divisions"]);
      division_names = conference_divisions[conference_name];
      for (j=0; j < division_names.length; j++) {
        division_teams= Array.prototype.slice.call(document.getElementsByClassName("team " + conference_name + " " + division_names[j]));
        divisions_div.appendChild(create_div(["division"], [create_div(["name"],division_names[j]),create_div(["teams"],division_teams)]));
      }
      conference_element = create_div(["conference"], create_div(["name"],conference_name));
      conference_element.appendChild(divisions_div);
      conferences_div.appendChild(conference_element);
    }
    league_element.removeChild(league_element.getElementsByClassName("name")[0]);
    league_element.removeChild(league_element.getElementsByClassName("teams")[0]);
    league_element.appendChild(conferences_div);
    last_resort_function();
  };

  var remove_conferences_divisions = function () {
    var league_element = document.getElementById("league");
    var all_teams = document.getElementsByClassName("team");
    var conferences_element = document.getElementById("conferences");
    var new_teams = create_div(["teams"],all_teams);
    league_element.removeChild(conferences_element);
    league_element.appendChild(create_div(["name"],"League"));
    league_element.appendChild(new_teams);
    last_resort_function();
  };


  var conferences_toggle = function() {
    var selector_element = this;
    var league_selector = document.getElementById("league_select");
    if ( selector_element.classList.contains("selected")) {
      add_divisions();
      selector_element.classList.remove("selected");
    } else if (league_selector.classList.contains("selected")) {
      add_conferences();
      league_selector.classList.remove("selected");
      selector_element.classList.add("selected");
    } else {
      remove_divisions();
      selector_element.classList.add("selected");
    }
  };

  var league_toggle = function() {
    var selector_element = this;
    var select_our_element = !(selector_element.classList.contains("selected"));
    if ( selector_element.classList.contains("selected")) {
      add_conferences_divisions();
    } else {
      remove_conferences_divisions();
    }
    var siblings = getSiblings(selector_element, hasClassList);
    for (var i=0; i < siblings.length; i++) {
      siblings[i].classList.remove("selected");
    }
    if (select_our_element) {
      selector_element.classList.add("selected");
    }
  };

  document.getElementById("conference_select").addEventListener("click", conferences_toggle, false);
  document.getElementById("league_select").addEventListener("click", league_toggle, false);

  var add_mark = function(mark,teams) {
    for (var i=0; i<teams.length; i++) {
      teams[i].getElementsByClassName("icon")[0].appendChild(create_div(["icon_mark"],mark));
    }
  };

  var remove_marks = function(marks) {
    var i;
    var teams = document.getElementsByClassName("team");
    var icon;
    var marks_regex = new RegExp(marks.join("|"));
    for (i=0; i<teams.length; i++) {
      icon = teams[i].getElementsByClassName("icon")[0];
      icon.innerHTML = icon.innerHTML.replace(marks_regex,"");
    }
  };

  var top_3_marks = [ "\u25F9", "\u25FF", "\u25FA", "\u25F8"];
  var playoff_marks = [ "\u25BB", "\u25C5"];

  var mark_top_3 = function (marks) {
    var i;
    var conferences = Object.keys(conference_divisions);
    var divisions=[];
    var teams;
    for (i=0; i<conferences.length; i++) { divisions = divisions.concat(conference_divisions[conferences[i]]); }
    for (i=0; i<divisions.length; i++) {
      teams = sorted_teams(document.getElementsByClassName("team " + divisions[i]));
      add_mark(marks[i],teams.slice(0,3));
    }
  };

  var mark_playoffs = function(marks, top_3_marked) {
    var i,j;
    var conferences = Object.keys(conference_divisions);
    var divisions=[];
    var teams;
    var playoff_teams;
    var team_index;
    for (i=0; i<conferences.length; i++) {
      playoff_teams=[];
      divisions = conference_divisions[conferences[i]];
      for (j=0; j<divisions.length; j++) {
        teams = sorted_teams(document.getElementsByClassName("team " + divisions[j]));
        playoff_teams = playoff_teams.concat(teams.slice(0,3));
      }
      teams = sorted_teams(document.getElementsByClassName("team " + conferences[i]));
      for (j=0; j < playoff_teams.length; j++) {
        team_index = teams.indexOf(playoff_teams[j]);
        if (team_index > -1) { teams.splice(team_index,1);}
      }
      if (!(top_3_marked)){ add_mark(marks[i], playoff_teams); }
      add_mark(marks[i], teams.slice(0,2));
    }
  };

  var top_3_toggle = function() {
    var selector_element = this;
    var playoffs_marked = document.getElementById("playoffs_select").classList.contains("selected");
    if ( selector_element.classList.contains("selected")) {
      if (playoffs_marked) {
        remove_marks(top_3_marks);
        remove_marks(playoff_marks);
        mark_playoffs(playoff_marks);
      } else {
        remove_marks(top_3_marks);
      }
    } else {
      if (playoffs_marked) {
        remove_marks(playoff_marks);
        mark_top_3(top_3_marks);
        mark_playoffs(playoff_marks, true);
      } else {
        mark_top_3(top_3_marks);
      }
    }
    selector_element.classList.toggle("selected");
  };

  var playoffs_toggle = function() {
    var selector_element = this;
    var top_3_marked = document.getElementById("top_3_select").classList.contains("selected");
    if ( selector_element.classList.contains("selected")) {
      remove_marks(playoff_marks);
    } else {
      mark_playoffs(playoff_marks, top_3_marked);
    }
    selector_element.classList.toggle("selected");
  };

  document.getElementById("top_3_select").addEventListener("click", top_3_toggle, false);
  document.getElementById("playoffs_select").addEventListener("click", playoffs_toggle, false);

});
