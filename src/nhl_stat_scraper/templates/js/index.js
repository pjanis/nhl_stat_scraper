document.addEventListener("DOMContentLoaded", function() {
  var i,j;
  var default_for = function (arg, val) { return typeof arg !== "undefined" ? arg : val; };

  var games = document.querySelectorAll(".results > svg[class*=game_]:not(.game_divider)");

  var hidden_classes = ["hidden","last_n_hidden"];

  var displayed_games = function(scoping_element){
    scoping_element = default_for(scoping_element, document);
    var i;
    var not_hidden_selector = "";
    for (i = 0; i < hidden_classes.length; i++) {
      not_hidden_selector += ":not(." + hidden_classes[i] + ")";
    }
    return scoping_element.querySelectorAll(".results > svg[class*=game_]:not(.game_divider)" + not_hidden_selector);
  };

  var game_data = JSON.parse(document.getElementById("game_data").innerHTML);

  var format_date = function(date) {
    var day = date.getDate();
    var month = date.getMonth() + 1;
    var year = date.getFullYear();

    if (day < 10 ) { day = "0" + day; }
    if (month < 10 ) { month = "0" + month; }

    return [year,month,day].join("-");
  };

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
  var home_incomplete = ["home_incomplete", "home_upcoming", "home_ongoing"];
  var away_incomplete = ["away_incomplete", "away_upcoming", "away_ongoing"];
  var home_classes = home_incomplete.concat(home_wins).concat(home_losses).concat(home_otl);
  var away_classes = away_incomplete.concat(away_wins).concat(away_losses).concat(away_otl);
  var win_classes = home_wins.concat(away_wins);
  var row_wins = home_row.concat(away_row);
  var loss_classes = ["home_loss", "away_loss"];
  var extra_time_loss_classes = ["home_ot_loss","home_so_loss","away_ot_loss","away_so_loss"];
  var all_classes = []; //classes that belong to the complete display (home + away)

  var append_content = function(base_element, content) {
    if (content === undefined) {
      base_element.innerHTML += "";
    } else if (Array.isArray(content)) {
      for (var i=0; i < content.length; i++) {
        append_content(base_element, content[i]);
      }
    } else if ((HTMLCollection.prototype.isPrototypeOf(content)) || (NodeList.prototype.isPrototypeOf(content))) {
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
    var self = this;
    var element_index = Array.prototype.indexOf.call(displayed_games(self.parentNode), self);
    var results = document.getElementsByClassName("results");
    var i;
    var games;
    for (i = 0; i < results.length; i++) {
      games = displayed_games(results[i]);
      games[element_index].classList.toggle("hovered_bout");
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

  var toggle_class_on_classes = function(class_name, classes, scoping_element) {
    scoping_element = default_for(scoping_element, document);
    var elements;
    for (var i=0; i < classes.length; i++) {
      elements = scoping_element.getElementsByClassName(classes[i]);
      for (var j=0, el_length= elements.length; j < el_length; j++) {
        elements[j].classList.toggle(class_name);
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
    var i;
    var sum=0;
    if (Array.isArray(el)) {
      for (i=0; i < el.length; i++) {
        sum += count_occurences_of_classes(el[i],classes);
      }
    } else if ((HTMLCollection.prototype.isPrototypeOf(el)) || (NodeList.prototype.isPrototypeOf(el))) {
      sum += count_occurences_of_classes(Array.prototype.slice.call(el), classes);
    } else if (hasClassList(el)) {
      for (i=0; i < classes.length; i++) {
        if (el.classList.contains(classes[i])) { sum += 1; }
        sum += el.getElementsByClassName(classes[i]).length;
      }
    }

    return sum;
  };

  var limit_results = function(results, n,result_classes){
    var matched_results = results.querySelectorAll('.'+result_classes.join(', .'));
    return Array.prototype.slice.call(matched_results).slice(-1*n);
  };

  var menu_toggle = function() {
    var self = this;
    var siblings = getSiblings(self, hasClassList);
    var i;
    for (i=0; i < siblings.length; i++) {
      if (self != siblings[i]) {
        siblings[i].classList.toggle("hidden");
      }
    }
    self.classList.toggle("selected");
  };

  document.getElementById("display_options_toggle").addEventListener("click",menu_toggle,false);
  document.getElementById("key_select").addEventListener("click",menu_toggle,false);

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
  var displayed_team_pts = function(team) { return pts(displayed_games(team), win_classes, extra_time_loss_classes);};
  var displayed_team_row = function(team) { return regulation_overtime_wins(displayed_games(team), row_wins);};
  var displayed_team_record = function(team) { return record(displayed_games(team), win_classes, loss_classes, extra_time_loss_classes); };
  var displayed_team_record_with_row = function(team) { return record_with_row(displayed_games(team), win_classes, loss_classes, extra_time_loss_classes, row_wins); };
  var last_5_team_pts = function(team) {
    return pts(
      limit_results(team.getElementsByClassName("results")[0], 5, win_classes.concat(loss_classes).concat(extra_time_loss_classes)),
      win_classes,
      extra_time_loss_classes);};
  var last_5_team_home_pts = function(team) {
    return pts(
      limit_results(team.getElementsByClassName("results")[0], 5, home_wins.concat(home_losses).concat(home_otl)),
      home_wins,
      home_otl);};
  var last_5_team_away_pts = function(team) {
    return pts(
      limit_results(team.getElementsByClassName("results")[0], 5, away_wins.concat(away_losses).concat(away_otl)),
      away_wins,
      away_otl);};
  var last_5_team_row = function(team) {
    return regulation_overtime_wins(
      limit_results(team.getElementsByClassName("results")[0], 5, win_classes.concat(loss_classes).concat(extra_time_loss_classes)),
      win_classes,
      extra_time_loss_classes);};
  var last_5_team_home_row = function(team) {
    return regulation_overtime_wins(
      limit_results(team.getElementsByClassName("results")[0], 5, home_wins.concat(home_losses).concat(home_otl)),
      home_wins,
      home_otl);};
  var last_5_team_away_row = function(team) {
    return regulation_overtime_wins(
      limit_results(team.getElementsByClassName("results")[0], 5, away_wins.concat(away_losses).concat(away_otl)),
      away_wins,
      away_otl);};
  var last_10_team_pts = function(team) {
    return pts(
      limit_results(team.getElementsByClassName("results")[0], 10, win_classes.concat(loss_classes).concat(extra_time_loss_classes)),
      win_classes,
      extra_time_loss_classes);};
  var last_10_team_home_pts = function(team) {
    return pts(
      limit_results(team.getElementsByClassName("results")[0], 10, home_wins.concat(home_losses).concat(home_otl)),
      home_wins,
      home_otl);};
  var last_10_team_away_pts = function(team) {
    return pts(
      limit_results(team.getElementsByClassName("results")[0], 10, away_wins.concat(away_losses).concat(away_otl)),
      away_wins,
      away_otl);};
  var last_10_team_row = function(team) {
    return regulation_overtime_wins(
      limit_results(team.getElementsByClassName("results")[0], 10, win_classes.concat(loss_classes).concat(extra_time_loss_classes)),
      win_classes,
      extra_time_loss_classes);};
  var last_10_team_home_row = function(team) {
    return regulation_overtime_wins(
      limit_results(team.getElementsByClassName("results")[0], 10, home_wins.concat(home_losses).concat(home_otl)),
      home_wins,
      home_otl);};
  var last_10_team_away_row = function(team) {
    return regulation_overtime_wins(
      limit_results(team.getElementsByClassName("results")[0], 10, away_wins.concat(away_losses).concat(away_otl)),
      away_wins,
      away_otl);};

  var default_sort_functions= [team_pts, team_row];

  var sorted_teams = function(teams, sort_functions) {  //sort_functions are functions to run on team_results to sort
    if ((HTMLCollection.prototype.isPrototypeOf(teams)) || (NodeList.prototype.isPrototypeOf(teams))) {
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

  var home_resort = function() { resort_teams([team_home_pts,team_home_row]); };
  var away_resort = function() { resort_teams([team_away_pts,team_away_row]); };
  var last_5_resort = function () { resort_teams([last_5_team_pts,last_5_team_row]);};
  var last_10_resort = function () { resort_teams([last_10_team_pts, last_10_team_row]);};
  var last_5_home_resort = function () { resort_teams([last_5_team_home_pts, last_5_team_home_row]);};
  var last_5_away_resort = function () { resort_teams([last_5_team_away_pts, last_5_team_away_row]);};
  var last_10_home_resort = function () { resort_teams([last_10_team_home_pts, last_10_team_home_row]);};
  var last_10_away_resort = function () { resort_teams([last_10_team_away_pts, last_10_team_away_row]);};
  var last_resort_function = resort_teams;
  var last_n_windowing = undefined;  // undefined for none or int value of n

  var resort_function_from_string = function(sort_name) {
    return { "" : resort_teams,
             "home" : home_resort,
             "away" : away_resort,
             "last_5" : last_5_resort,
             "last_10" : last_10_resort,
             "last_5_home" : last_5_home_resort,
             "last_5_away" : last_5_away_resort,
             "last_10_home" : last_10_home_resort,
             "last_10_away" : last_10_away_resort
           }[sort_name];
  };

  var resort_listener_template = function(self,resort_function) {
    resort_function();
    last_resort_function=resort_function;
    document.getElementById("home_away_select").classList.remove("sort_selected");
    document.getElementById("home_select").classList.remove("sort_selected");
    document.getElementById("away_select").classList.remove("sort_selected");
    document.getElementById("last_5_select").classList.remove("sort_selected");
    document.getElementById("last_10_select").classList.remove("sort_selected");
    if ((resort_function == home_resort) ||
        (resort_function == last_5_home_resort) ||
        (resort_function == last_10_home_resort)) {
      document.getElementById("home_select").classList.add("sort_selected");
    } else if ((resort_function == away_resort) ||
               (resort_function == last_5_away_resort) ||
               (resort_function == last_10_away_resort)) {
      document.getElementById("away_select").classList.add("sort_selected");
    } else if ((resort_function == resort_teams) ||
               (resort_function == last_5_resort) ||
               (resort_function == last_10_resort)) {
      document.getElementById("home_away_select").classList.add("sort_selected");
    }
    if ((resort_function == last_5_resort) ||
        (resort_function == last_5_home_resort) ||
        (resort_function == last_5_away_resort)) {
      document.getElementById("last_5_select").classList.add("sort_selected");
    } else if ((resort_function == last_10_resort) ||
               (resort_function == last_10_home_resort) ||
               (resort_function == last_10_away_resort)) {
      document.getElementById("last_10_select").classList.add("sort_selected");
    }

    self.remove();
  };

  var current_resort_function = function () {
    var selected_elements =[];
    if (document.getElementById("last_5_select").classList.contains("selected")) { selected_elements.push("last_5"); }
    if (document.getElementById("last_10_select").classList.contains("selected")) { selected_elements.push("last_10"); }
    if (document.getElementById("home_select").classList.contains("selected")) { selected_elements.push("home"); }
    if (document.getElementById("away_select").classList.contains("selected")) { selected_elements.push("away"); }
    return resort_function_from_string(selected_elements.join("_"));
  };

  var add_resort_element = function (container_el) {
    var current_func = current_resort_function();
    if (last_resort_function != current_func ) {
      var resort_div = create_div(["resort_select"],"Re-Sort");
      container_el.appendChild(resort_div);
      var resort_listener_function = function() { var self = this; resort_listener_template(self,current_func);};
      resort_div.addEventListener("click",resort_listener_function,false);
    }
  };

  var update_team_details = function() {
    var i;
    var record_function;
    var teams = document.getElementsByClassName("team");
    if ( teams[0].getElementsByClassName("record")[0].classList.contains("record_with_row")) {
      record_function = displayed_team_record_with_row;
    } else {
      record_function = displayed_team_record;
    }

    var record_element;
    for (i=0; i < teams.length; i++) {
      record_element = teams[i].getElementsByClassName("record")[0];
      record_element.innerHTML = record_function(teams[i]);

      record_element = teams[i].getElementsByClassName("pts")[0];
      record_element.innerHTML = displayed_team_pts(teams[i]);
    }
  };

  var home_away_toggle = function( self, our_classes, their_classes ) {
    var select_our_classes = !(self.classList.contains("selected"));
    hidden_toggle(select_our_classes, our_classes, their_classes);
    if (last_n_windowing != undefined) {
      show_hidden_n_games();
      last_n_games(last_n_windowing);
    };
    update_team_details();
    var siblings = getSiblings(self, hasClassList);
    for (var i=0; i < siblings.length; i++) {
      siblings[i].classList.remove("selected");
    }

    if (select_our_classes) { self.classList.add("selected"); }
    remove_resort_element(self.parentElement);
    add_resort_element(self.parentElement);
  };

  var home_select = document.getElementById("home_select");
  var away_select = document.getElementById("away_select");

  var home_toggle =  function(){
    var self = this;
    home_away_toggle(self, home_classes, away_classes.concat(all_classes));
  };

  var away_toggle =  function(){
    var self = this;
    home_away_toggle(self, away_classes, home_classes.concat(all_classes));
  };

  home_select.addEventListener("click", home_toggle, false);
  away_select.addEventListener("click", away_toggle, false);

  var row_toggle = function() {
    var i;
    var record_function;
    var selector_element = this;
    if ( selector_element.classList.contains("selected")) {
      record_function = displayed_team_record;
    } else {
      record_function = displayed_team_record_with_row;
    }
    selector_element.classList.toggle("selected");

    var teams = document.getElementsByClassName("team");
    var record_element;
    for (i=0; i < teams.length; i++) {
      record_element = teams[i].getElementsByClassName("record")[0];
      record_element.innerHTML = record_function(teams[i]);
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

  var toggle_days_games = function(toggle_class,day) {
    var i;
    var day_str = format_date(default_for(day,new Date));
    var league_scope = document.getElementById("league");
    for (i=0; i<game_data.length; i++) {
      if (game_data[i]["game-date"] === day_str) {
        toggle_class_on_classes(toggle_class,["game_"+game_data[i]["game-id"]],league_scope);
      }
    }
  };

  var todays_games_toggle = function() {
    var self = this;
    self.classList.toggle("selected");
    toggle_days_games("todays_game",new Date);
  };
  var yesterdays_games_toggle = function() {
    var self = this;
    self.classList.toggle("selected");
    toggle_days_games("yesterdays_game",(function (d) { d.setDate(d.getDate()-1); return d;})(new Date));
  };
  var tomorrows_games_toggle = function() {
    var self = this;
    self.classList.toggle("selected");
    toggle_days_games("tomorrows_game",(function (d) { d.setDate(d.getDate()+1); return d;})(new Date));
  };

  document.getElementById("today_select").addEventListener("click", todays_games_toggle, false);
  document.getElementById("tomorrow_select").addEventListener("click", tomorrows_games_toggle, false);
  document.getElementById("yesterday_select").addEventListener("click", yesterdays_games_toggle, false);

  var last_n_games = function(n) {
    var i,j;
    var teams = document.getElementsByClassName("team");
    var games;
    add_class_to_classes("last_n_hidden",
                         home_incomplete.concat(away_incomplete).concat(["game_divider"]),
                         document.getElementById("league"));
    for (i = 0; i < teams.length; i++) {
      games = teams[i].querySelectorAll(".results > svg[class*=game_]:not(.hidden):not(.last_n_hidden)");
      games = Array.prototype.slice.call(games);
      games = games.sort(function(a,b){
        var bout_a =a.getAttribute("class").split(" ").find(function (el) {
          return el.indexOf("bout_") >= 0;
        });
        var bout_b =b.getAttribute("class").split(" ").find(function (el) {
          return el.indexOf("bout_") >= 0;
        });
        return parseInt(bout_a.slice(bout_a.indexOf("_")+1)) - parseInt(bout_b.slice(bout_b.indexOf("_")+1));
      });
      for (j = 0; j < (games.length - n); j++) {
        games[j].classList.add("last_n_hidden");
      }
    }
  };

  var show_hidden_n_games = function() {
    var result_svgs = document.querySelectorAll(".results > svg");
    result_svgs = Array.prototype.slice.call(result_svgs);
    for (var i=0; i<result_svgs.length; i++) {
      result_svgs[i].classList.remove("last_n_hidden");
    }
  };

  var last_n_toggle = function(self,n) {
    var toggle_selected = !(self.classList.contains("selected"));
    var siblings = getSiblings(self, hasClassList);
    for (var i=0; i < siblings.length; i++) {
      siblings[i].classList.remove("selected");
    }
    show_hidden_n_games();
    if (toggle_selected) {
      last_n_games(n);
      last_n_windowing = n;
      self.classList.add("selected");
    } else {
      last_n_windowing = undefined;
      show_hidden_n_games();
    }
    update_team_details();

    var home_away_select = document.getElementById("home_away_select");
    remove_resort_element(home_away_select);
    add_resort_element(home_away_select);
  };

  var last_5_toggle = function() { var self = this; last_n_toggle(self, 5) };
  var last_10_toggle = function() { var self = this; last_n_toggle(self, 10) };

  document.getElementById("last_5_select").addEventListener("click", last_5_toggle, false);
  document.getElementById("last_10_select").addEventListener("click", last_10_toggle, false);
});
