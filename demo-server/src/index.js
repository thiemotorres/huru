// Huru demo server: a tiny Xtream Codes-compatible API for testing the app (and for app store reviewers).
// Content is legal only: Creative Commons open movies and public test streams provided by streaming vendors.
// Login: username "demo", password "demo".

const USER = "demo";
const PASS = "demo";

// Live "channels": public HLS test streams (Akamai, Unified Streaming, Mux, Apple).
const LIVE_CATEGORIES = [
  { category_id: "1", category_name: "Test streams", parent_id: 0 },
  { category_id: "2", category_name: "Open movies", parent_id: 0 },
];
const LIVE = [
  { id: 1, name: "Akamai Live Test HD", cat: "1", url: "https://cph-p2p-msl.akamaized.net/hls/live/2000341/test/master.m3u8",
    shows: ["Live Test Pattern", "Clock & Tone", "Signal Check"] },
  { id: 2, name: "Apple BipBop", cat: "1", url: "https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_fmp4/master.m3u8",
    shows: ["BipBop", "Colour Bars", "Audio Sync"] },
  { id: 3, name: "Tears of Steel FHD", cat: "2", url: "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8",
    shows: ["Tears of Steel", "Making Of", "Tears of Steel"] },
  { id: 4, name: "Big Buck Bunny", cat: "2", url: "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
    shows: ["Big Buck Bunny", "Short Films", "Big Buck Bunny"] },
];

// Movies and series: Blender open movies (CC BY), hosted by Blender and the Internet Archive.
const FILMS = {
  bbb: { name: "Big Buck Bunny (2008)", ext: "mp4", poster: "poster0", url: "https://archive.org/download/BigBuckBunny_124/Content/big_buck_bunny_720p_surround.mp4",
         plot: "A giant rabbit takes revenge on three bullying rodents. Blender Foundation, CC BY 3.0." },
  sintel: { name: "Sintel (2010)", ext: "mp4", poster: "poster2", url: "https://archive.org/download/Sintel/sintel-2048-surround.mp4",
            plot: "A girl searches for her lost baby dragon. Blender Foundation, CC BY 3.0." },
  tos: { name: "Tears of Steel (2012)", ext: "mov", poster: "poster4", url: "https://download.blender.org/demo/movies/ToS/tears_of_steel_720p.mov",
         plot: "Scientists try to save the world from robots in Amsterdam. Blender Foundation, CC BY 3.0." },
  ed: { name: "Elephants Dream (2006)", ext: "mov", poster: "poster5", url: "https://download.blender.org/ED/elephantsdream-720-h264-st-aac.mov",
        plot: "Two characters explore a strange, endless machine. Blender Foundation, CC BY 2.5." },
};
const VOD_CATEGORIES = [{ category_id: "10", category_name: "Open movies", parent_id: 0 }];
const MOVIES = [
  { id: 101, film: "bbb" }, { id: 102, film: "sintel" }, { id: 103, film: "tos" }, { id: 104, film: "ed" },
];
const SERIES_CATEGORIES = [{ category_id: "20", category_name: "Demo series", parent_id: 0 }];
const SERIES = [
  { id: 1, name: "Blender Open Movies", poster: "poster7", plot: "The classic open movies of the Blender Foundation, one per episode.",
    episodes: [
      { id: "1001", season: 1, num: 1, film: "ed" }, { id: "1002", season: 1, num: 2, film: "bbb" },
      { id: "1003", season: 2, num: 1, film: "sintel" }, { id: "1004", season: 2, num: 2, film: "tos" },
    ] },
  { id: 2, name: "Streaming Test Patterns", poster: "poster13", plot: "Public HLS test streams – handy to check playback.",
    episodes: [
      { id: "2001", season: 1, num: 1, title: "Apple BipBop", ext: "m3u8", url: LIVE[1].url },
      { id: "2002", season: 1, num: 2, title: "Big Buck Bunny (HLS)", ext: "m3u8", url: LIVE[3].url },
    ] },
];

const json = (data) => new Response(JSON.stringify(data), { headers: { "content-type": "application/json; charset=utf-8" } });
const b64 = (s) => btoa(String.fromCharCode(...new TextEncoder().encode(s)));
const episodeFields = (e) => e.film
  ? { title: FILMS[e.film].name, ext: FILMS[e.film].ext, url: FILMS[e.film].url, plot: FILMS[e.film].plot }
  : { title: e.title, ext: e.ext, url: e.url, plot: null };

/** Rolling programme guide: alternating 30/45/60 min slots aligned to the half hour, so it always looks current. */
function shortEpg(channel, limit) {
  const now = Math.floor(Date.now() / 1000);
  let start = now - (now % 1800) - 1800;
  const listings = [];
  for (let i = 0; listings.length < limit; i++) {
    const len = [30, 45, 60][(channel.id + i) % 3] * 60;
    if (start + len > now) {
      listings.push({ title: b64(channel.shows[i % channel.shows.length]), description: b64(""),
                      start_timestamp: String(start), stop_timestamp: String(start + len) });
    }
    start += len;
  }
  return { epg_listings: listings };
}

function api(url, origin) {
  const p = url.searchParams;
  if (p.get("username") !== USER || p.get("password") !== PASS) return json({ user_info: { auth: 0 } });
  const poster = (name) => `${origin}/posters/${name}.png`;
  switch (p.get("action")) {
    case null:
      return json({
        user_info: { auth: 1, status: "Active", username: USER, password: PASS, exp_date: null, max_connections: "1",
                     allowed_output_formats: ["m3u8"] },
        server_info: { url: url.hostname, server_protocol: "https", timezone: "UTC", timestamp_now: Math.floor(Date.now() / 1000) },
      });
    case "get_live_categories": return json(LIVE_CATEGORIES);
    case "get_live_streams":
      return json(LIVE.map((c) => ({ num: c.id, name: c.name, stream_type: "live", stream_id: c.id, stream_icon: "",
                                     epg_channel_id: null, category_id: c.cat })));
    case "get_short_epg": {
      const channel = LIVE.find((c) => String(c.id) === p.get("stream_id"));
      return json(channel ? shortEpg(channel, Number(p.get("limit") || 4)) : { epg_listings: [] });
    }
    case "get_vod_categories": return json(VOD_CATEGORIES);
    case "get_vod_streams":
      return json(MOVIES.map((m) => ({ num: m.id, name: FILMS[m.film].name, stream_type: "movie", stream_id: m.id,
                                       stream_icon: poster(FILMS[m.film].poster), category_id: "10",
                                       container_extension: FILMS[m.film].ext })));
    case "get_series_categories": return json(SERIES_CATEGORIES);
    case "get_series":
      return json(SERIES.map((s) => ({ series_id: s.id, name: s.name, cover: poster(s.poster), plot: s.plot, category_id: "20" })));
    case "get_series_info": {
      const s = SERIES.find((x) => String(x.id) === p.get("series_id"));
      if (!s) return json({ info: {}, episodes: {} });
      const episodes = {};
      for (const e of s.episodes) {
        const f = episodeFields(e);
        (episodes[e.season] ??= []).push({ id: e.id, episode_num: e.num, title: f.title, container_extension: f.ext,
                                           season: e.season, info: { plot: f.plot } });
      }
      return json({ info: { name: s.name, plot: s.plot, cover: poster(s.poster) }, episodes });
    }
    default: return json([]);
  }
}

/** /live|movie|series/{user}/{pass}/{id}.{ext} → redirect to the real stream. Live is HLS only: .ts answers 404. */
function stream(kind, user, pass, id, ext) {
  if (user !== USER || pass !== PASS) return new Response("Unauthorized", { status: 401 });
  let target;
  if (kind === "live") target = ext === "m3u8" && LIVE.find((c) => String(c.id) === id)?.url;
  if (kind === "movie") { const m = MOVIES.find((x) => String(x.id) === id); target = m && FILMS[m.film].url; }
  if (kind === "series") {
    const e = SERIES.flatMap((s) => s.episodes).find((x) => x.id === id);
    target = e && episodeFields(e).url;
  }
  return target ? Response.redirect(target, 302) : new Response("Not found", { status: 404 });
}

export default {
  async fetch(request) {
    const url = new URL(request.url);
    if (url.pathname === "/player_api.php") return api(url, url.origin);
    const m = url.pathname.match(/^\/(live|movie|series)\/([^/]+)\/([^/]+)\/([^/.]+)\.(\w+)$/);
    if (m) return stream(m[1], decodeURIComponent(m[2]), decodeURIComponent(m[3]), m[4], m[5]);
    if (url.pathname === "/") {
      return new Response(
        "Huru demo server – Xtream Codes-compatible test API with legal content only.\n" +
        `Server: ${url.origin}  Username: demo  Password: demo\n` +
        "Content: Blender open movies (Creative Commons) and public HLS test streams.\n" +
        "https://github.com/thiemotorres/huru\n",
        { headers: { "content-type": "text/plain; charset=utf-8" } });
    }
    return new Response("Not found", { status: 404 });
  },
};
