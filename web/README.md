# web — JOIN landing page

A single self-contained `index.html` (no build, no deps). Flashy Minecraft / degen
"JOIN" sign with the server IP and a click-to-copy button.

## Set the server IP

Edit one line near the bottom of `index.html`:

```js
const SERVER_IP = "your-server-ip:25565";
```

Quick demo without editing — append a query param:

```
index.html?ip=play.example.com:25565
```

## Run / deploy

```bash
# local
open index.html            # or: python3 -m http.server -d web 8080

# GitHub Pages: Settings → Pages → deploy from /web
# Vercel / Netlify: drop the web/ folder, no config needed
```
