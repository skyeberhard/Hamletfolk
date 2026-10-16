# The village dashboard

A web page the plugin serves, showing every village and, if you allow it, letting an admin act on them. It is the
plugin's admin console: what you would otherwise read from the server log and `/settlement admin`.

## Turning it on

In `plugins/Hamletfolk/config.yml`:

```yaml
dashboard:
  enabled: true          # serve the page
  port: 8123
  bind: 127.0.0.1        # leave this: see "Reaching it from another computer"
  refresh-seconds: 5
  allow-actions: false   # true adds the buttons (R1.36)
```

Restart the server and open `http://127.0.0.1:8123/` in a browser on the same machine. The console says where it is.

## What it shows

- **All villages**, worst off first: people, food, wood, stone, tools, treasury, beds, what each is building, and what is in its way
  (no free bed, food under what a newcomer needs, a material it lacks, idle adults, a project nobody builds, no lot for a building).
- **A village**: its stores, work, requests, what it wants next, the plan as a map (streets, square, lots ready, rough or built on,
  buildings), residents with their jobs and needs, buildings, projects, the planner's decisions and its history.

The page refreshes itself every few seconds.

## As it happens (R1.39)

- **The feed** ("As it happens", on the overview and on each village's page): every new line of every village's history as it is
  written, newest first, the last 400, with the village and how long ago. When the server starts it shows the last three lines of
  each village as "earlier". The filter narrows it to births, deaths, arrivals, departures, recoveries and trouble.
- **Small graphs**: each village's people, food, wood, stone, tools, treasury and beds are sampled once a minute (the last 240, so
  four hours) and drawn in the village list and on the village's cards.
- Both are kept in memory and start again when the server restarts. They are gathered even while nobody has the page open (cheap:
  new history lines and a few numbers a minute); the full village pages are still built only while someone is looking.
- **Moving about**: the address holds the village and tab (`#<village id>/plan`), so a view can be bookmarked or sent to another admin.
  The plan map zooms with the wheel, moves when dragged, and says what a lot or building is when pointed at; Fit puts it back.
- Endpoints: `GET /api/events?since=<n>` (the lines after sequence number n, the latest number and the id of this start of the server, so an open page starts over after a restart) and `GET /api/trends` (each
  village's samples, oldest first: people, food, wood, stone, tools, treasury, beds; built only while someone is looking).

## Actions (R1.36)

With `allow-actions: true`, a village's page has an Admin panel and each resident's job is a picker. The actions are a closed list
(`AdminAction.Kind` in core): save, replan, warp, fast-forward, resettle, cancel project, unstick, mend palisade, rename, set a
resident's job (pins them), unpin. There is no free-text console: nothing outside that list can run.

Every action:

1. is checked in core first (the village exists, the resident lives there, numbers are whole and in range, a name is short and plain);
2. must be confirmed on the page if it cannot be undone or costs a lot (replan, warp, resettle, cancel, rename, mend the palisade, a
   speed above 20), and the request must say so; a child cannot be given a trade;
3. runs on the server's main thread through the same code as its `/settlement admin` command;
4. is written to `plugins/Hamletfolk/audit.log`, to the console and, for a village, to its history ("An admin (...) chose to ...").

Refused requests (actions off, wrong token, not JSON, too large, too fast, unknown action, bad parameter) are written to the audit
log too, at most 20 a minute (the rest are counted), and the log is moved to `audit.log.1` when it passes 5 MB.

## How it is protected

- **Bound to 127.0.0.1** by default: only the machine the server runs on can reach it. Actions are **refused to start** if `bind`
  is anything else: the page token keeps other websites out, not other computers (anyone who can reach the port could read the page
  and its token). Other people with an account on the server machine can reach it too: keep it to a machine only admins use.
- **Host check:** a request naming another host is refused, so a web page elsewhere cannot read it through your browser.
- **Page token:** each start makes a random token, written into the page it serves. An action must carry it in a header, which a page on
  another site cannot read or send (the browser would ask first, and the server never says yes). Compared in constant time.
- **JSON only**, at most 4 KB, one action at a time (a second is turned away, not queued) and no faster than one every 0.75 s.
- **Headers:** no framing, no referrer, and a content security policy that runs only the page's own script (a fresh nonce each start).
- **No secrets** in the data: it shows what `/settlement` and `/settlement admin` show.

## Reaching it from another computer (R1.37, planned)

Do **not** change `bind` to `0.0.0.0` or open the port in a router: the page has no password of its own yet. The plan is:

1. Keep `bind: 127.0.0.1`.
2. Publish it through **Cloudflare Tunnel** (as `ssh.goodecraft.com` already is), e.g. `admin.goodecraft.com` to `http://127.0.0.1:8123`.
3. Put **Cloudflare Access** in front of that hostname (free up to 50 users): only the admins' e-mail addresses (one-time code) or
   GitHub accounts get through.
4. The plugin will then check Cloudflare's signed header (`Cf-Access-Jwt-Assertion`) against the team's public keys, so a request that
   did not come through Access is refused even if the tunnel is misconfigured, and the signed-in address becomes the name in the audit
   log. Roles: a viewer list and an admin list in the config. (The tunnel passes the public host name, so R1.37 also adds it to the
   names the Host check accepts, or the tunnel's `httpHostHeader` is set to `127.0.0.1:8123`.)

Until R1.37 is built, reach it from another computer with an SSH tunnel: `ssh -L 8123:127.0.0.1:8123 <server>` and open
`http://127.0.0.1:8123/` locally.

## Room to grow

The page is built to take more without a rewrite: a village's tabs are one list (`TABS` in dashboard.html: an id, a label and a
function that draws it), the sidebar has a "More" section for other plugins' panels, and the feed and graphs take any village.

- **Panels from other plugins (R1.38, planned):** the dashboard will offer a small extension point through Bukkit's services manager,
  so another plugin (GoodeCraft's portal network, say) can add its own page, data and actions, checked and audited the same way,
  without Hamletfolk depending on it.
- **Plan overrides (R8.23, planned):** lock a lot, change a lot's kind, forbid a building, put a project first.

Code: `core/Dashboard`, `core/VillageBlockers`, `core/AdminAction`, `core/DashboardFeed` (tested); `paper/DashboardServer`, `paper/DashboardActions`,
`paper/src/main/resources/dashboard.html`.
