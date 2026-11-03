/* The screensaver: starts on the home screen's wallpaper, then a new photo every 2 minutes, and the clock and verse
   change sides each time (no text stays in one place for long). Reads the home screen's saved settings. */
(function () {
  'use strict';
  var WALLS = window.CleanFrameWallpapers || [], VERSES = window.InicioVerses || [];
  var PHOTO_MS = 120000, DAYS = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];
  var MONTHS = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];
  var $ = function (id) { return document.getElementById(id); };
  var photos = [$('photoA'), $('photoB')], shown = 0, wall = 0, side = 0;

  function savedWall() {
    try {
      var s = JSON.parse(localStorage.getItem('cleanframe.home.v1') || '{}'), id = s.wallpaper || (s.look && s.look.wall);
      for (var i = 0; i < WALLS.length; i++) if (WALLS[i].id === id) return i;
    } catch (e) {}
    return 0;
  }
  function fit() {
    var scale = Math.min(window.innerWidth / 1920, window.innerHeight / 1080), stage = $('stage');
    stage.style.transform = 'scale(' + scale + ')';
    stage.style.left = ((window.innerWidth - 1920 * scale) / 2) + 'px';
    stage.style.top = ((window.innerHeight - 1080 * scale) / 2) + 'px';
  }
  /* The same verse as the home screen (main.js, verseHtml): YouVersion's, saved by the home screen today, or the
     offline list. */
  function verse() {
    var d = new Date(), v = null, saved, today = d.getFullYear() + '-' + (d.getMonth() < 9 ? '0' : '') + (d.getMonth() + 1) + '-' + (d.getDate() < 10 ? '0' : '') + d.getDate();
    try { saved = JSON.parse(localStorage.getItem('inicio.verse') || 'null'); } catch (e) { saved = null; }
    if (saved && saved.date === today && saved.text) v = saved;
    else if (VERSES.length) v = VERSES[Math.floor(Date.UTC(d.getFullYear(), d.getMonth(), d.getDate()) / 864e5) % VERSES.length];
    if (!v) return;
    $('verseText').textContent = v.text;
    $('verseRef').textContent = v.ref;
  }
  function clock() {
    var d = new Date(), m = d.getMinutes();
    $('clock').textContent = d.getHours() + ':' + (m < 10 ? '0' : '') + m;
    $('date').textContent = DAYS[d.getDay()] + ', ' + d.getDate() + ' ' + MONTHS[d.getMonth()];
    verse();
    setTimeout(clock, 60000 - (Date.now() % 60000) + 30);
  }
  function showPhoto(i) {
    var next = photos[1 - shown], prev = photos[shown];
    if (!WALLS.length) return;
    next.onload = function () {
      next.className = 'photo';
      void next.offsetWidth;  // restart the drift from scale 1
      next.className = 'photo is-on is-drift';
      prev.className = 'photo is-drift';
      shown = 1 - shown;
    };
    next.src = WALLS[i].src;
  }
  function moveCard() {
    var card = $('card');
    card.className = 'card is-hidden' + (side ? ' is-right' : '');
    setTimeout(function () { side = 1 - side; card.className = 'card' + (side ? ' is-right' : ''); }, 1600);
  }
  function step() {
    wall = (wall + 1) % WALLS.length;
    showPhoto(wall);
    moveCard();
  }

  window.addEventListener('resize', fit);
  fit();
  wall = savedWall();
  showPhoto(wall);
  clock();
  setInterval(step, PHOTO_MS);
}());
