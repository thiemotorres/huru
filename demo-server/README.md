# Huru demo server

A tiny [Xtream Codes](https://github.com/thiemotorres/huru)-compatible API on Cloudflare Workers for testing Huru – and for app store reviewers who need working credentials.

**Legal content only:** Blender open movies (Creative Commons) and public HLS test streams provided by streaming vendors (Apple, Mux, Unified Streaming). Posters are fictional artwork from `tools/mock_assets.py`.

| | |
|---|---|
| Server | `https://huru-demo.feto.dev` |
| Username | `demo` |
| Password | `demo` |

Live streams are HLS only (`.ts` answers 404), which also exercises the app's HLS fallback.

```sh
cd demo-server
npx wrangler dev      # local: http://localhost:8787
npx wrangler deploy   # needs `npx wrangler login` once
```
