/* The one place to change apps: what's on the dock (in order), which installed app shows under each dock entry's name
   and mark, and which entries count as watching. Android reads nothing here on its own: the page passes it on.
   A personal apps.local.js, loaded right after this file and kept out of git, can change any of it for one TV. */
window.InicioApps = {
  /* id: the mark in assets/icons/<id>.svg. appId: an Android package, or a TV input (input.tv, input.hdmiN). */
  dock: [
    { id: 'youtube', name: 'YouTube', mark: true, appId: 'io.gh.reisxd.tizentube.cobalt' },
    { id: 'tv', name: 'TV', mark: true, appId: 'input.tv' },
    { id: 'netflix', name: 'Netflix', mark: true, appId: 'com.netflix.ninja' },
    { id: 'spotify', name: 'Spotify', mark: true, appId: 'com.spotify.tv.android' },
    { id: 'dazn', name: 'DAZN', mark: true, appId: 'com.dazn' },
    { id: 'ps5', name: 'PS5', mark: true, appId: 'input.hdmi3' },
    { id: 'twitch', name: 'Twitch', mark: true, appId: 'tv.twitch.android.app' }
  ],
  /* Installed package -> dock entry (TizenTube, a YouTube client for TVs, shows as YouTube). */
  brands: {
    'io.gh.reisxd.tizentube.cobalt': 'youtube', 'com.netflix.ninja': 'netflix', 'com.spotify.tv.android': 'spotify',
    'com.dazn': 'dazn', 'tv.twitch.android.app': 'twitch'
  },
  /* "Watched today": these entries' apps, plus Sony's TV app split into TV and PS5 (WatchTime.java). Spotify is music. */
  watched: ['youtube', 'netflix', 'dazn', 'twitch', 'tv', 'ps5']
};
