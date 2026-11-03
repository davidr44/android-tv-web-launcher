/* The TV side of the home screen: Android calls through the launcher's bridge (window.AndroidTV, see
   HomeActivity.java). In a desktop browser there is no bridge, so the controls say they need the TV. */
(function () {
  'use strict';
  /* Installed apps shown under a dock entry's name and mark (apps.js). */
  var BRANDS = window.InicioApps.brands;
  /* Left out of All apps: the official YouTube (TizenTube already shows as YouTube). */
  var HIDDEN = ['com.google.android.youtube.tv'];
  /* Colour icons in All apps for entries that show as something else: TizenTube as YouTube. */
  var ICONS = { 'io.gh.reisxd.tizentube.cobalt': '/icon/com.google.android.youtube.tv.png' };
  /* Not apps but TV inputs, listed with the apps. */
  var INPUTS = [{ appId: 'input.tv', name: 'TV', brand: 'tv', icon: '/icon/com.sony.dtv.tvx.png' },
    { appId: 'input.hdmi3', name: 'PS5', brand: 'ps5', icon: '/web/assets/icons/ps5-app.svg' }];

  function bridge() { return window.AndroidTV; }
  function unavailable(done) {
    done({ message: 'Open the launcher on the TV to use this control.', detail: 'The browser preview cannot control the TV.' });
  }
  function call(run, done) {
    if (!bridge()) { unavailable(done); return; }
    var error;
    try { error = run(bridge()); } catch (err) { error = err && err.message || 'Unknown error'; }
    if (error) done({ message: 'The TV could not open this screen.', detail: String(error) });
    else done(null);
  }
  function launchApp(id, done) { call(function (tv) { return tv.launch(id); }, done); }
  function launch(brand, done) { done({ message: 'This app has no launch target.' }); }
  function art(done) { done({ message: 'Not available on this TV.' }); }
  function home(done) { call(function (tv) { return tv.originalHome(); }, done); }
  function settings(done) { call(function (tv) { return tv.settings(); }, done); }
  function apps(done) {
    if (!bridge()) { unavailable(done); return; }
    var list;
    try { list = JSON.parse(bridge().apps()); } catch (err) { done({ message: 'Could not read installed apps.' }); return; }
    var result = INPUTS.slice();
    list.forEach(function (a) {
      if (HIDDEN.indexOf(a.appId) >= 0) return;
      result.push({ appId: a.appId, name: a.name, brand: BRANDS[a.appId] || '', icon: ICONS[a.appId] || a.icon || '' });
    });
    done(null, result);
  }
  console.log('viewport ' + window.innerWidth + 'x' + window.innerHeight + ' @' + window.devicePixelRatio);
  /* {"input.hdmiN": true} for the HDMI ports with a device on (the PS5 tile glows). */
  function inputs() {
    try { return bridge() ? JSON.parse(bridge().hdmiStates()) : {}; } catch (err) { return {}; }
  }
  /* The TV's navigation sound (HomeActivity leaves the arrows to the page, which knows if the focus moved). */
  function sound(direction) {
    try { if (bridge() && bridge().sound) bridge().sound(direction); } catch (err) {}
  }
  /* Today's minutes in the video apps, {total, apps: {youtube: m, ...}} (WatchTime.java), or null when unknown
     (no permission, browser preview). ps5Input: the PS5's HDMI port, "input.hdmiN". */
  function watchToday(ps5Input) {
    var counted = {}, watched = window.InicioApps.watched;
    Object.keys(BRANDS).forEach(function (pkg) { if (watched.indexOf(BRANDS[pkg]) >= 0) counted[pkg] = BRANDS[pkg]; });
    try { var json = bridge() && bridge().watchToday ? bridge().watchToday(JSON.stringify(counted), ps5Input || '') : ''; return json ? JSON.parse(json) : null; } catch (err) { return null; }
  }
  /* YouVersion's verse of the day, {ref, text} (VerseOfTheDay.java), or null until it's here (main.js keeps its own
     offline list for that). */
  function verseToday() {
    try { var json = bridge() && bridge().verseToday ? bridge().verseToday() : ''; return json ? JSON.parse(json) : null; } catch (err) { return null; }
  }
  window.FrameDevice = { launch: launch, launchApp: launchApp, art: art, home: home, settings: settings, apps: apps, inputs: inputs, sound: sound, watchToday: watchToday, verseToday: verseToday };
}());
