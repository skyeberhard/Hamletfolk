# Proposal for GoodeCraft: a shared admin dashboard

From Skylar (Hamletfolk), for Owen and the assistant that works on the GoodeCraft repository. A proposal to discuss, not a request
to merge anything. Nothing here changes GoodeCraft's code; the GoodeCraft side is entirely Owen's call.

## What exists now (Hamletfolk)

Hamletfolk serves a web dashboard from inside the plugin (the JDK's built-in HTTP server, no extra libraries): every village at a
glance, a page per village with a plan map, residents, buildings and history, and, if enabled, admin actions (replan, warp, set a
resident's job and so on). It is **off by default**, binds to `127.0.0.1`, and is read-only unless `dashboard.allow-actions: true`.
Actions are a closed, validated list; each is confirmed, runs on the main thread through the same code as the in-game admin command,
and is written to an audit log. Details: `docs/DASHBOARD.md` in the Hamletfolk repository.

Production GoodeCraft runs Hamletfolk `98d2f0b` (27 September), which has none of this. Any update goes through Owen's usual review,
pin and NAS backup.

## What we would like to agree

1. **Remote access, safely.** GoodeCraft already runs `cloudflared` (`ssh.goodecraft.com`) and Cloudflare in front of the site.
   Proposed: a hostname such as `admin.goodecraft.com` through the tunnel to `127.0.0.1:<port>`, with **Cloudflare Access** in
   front of it (only named admins, one-time e-mail code or GitHub). Hamletfolk would also verify Cloudflare's signed Access header
   itself, so nothing reaches the actions except through Access, and the signed-in address goes in the audit log. The port is never
   opened to the internet. Owen decides whether to do this, which hostname, and who is an admin.

2. **A place for GoodeCraft's own controls.** Hamletfolk would offer an open-ended extension point: through Bukkit's services
   manager, any plugin can register a panel (a name, data to show, and actions with their parameters). The dashboard draws it,
   enforces the same login, confirmation and audit, and runs the action on the main thread. Hamletfolk would not depend on
   GoodeCraft, and GoodeCraft would not depend on Hamletfolk beyond that small interface (soft dependency; if Hamletfolk is absent,
   nothing happens).

   What GoodeCraft puts there is up to Owen: portal routing, Portal Stones links, world clocks, rifts, the dragon event, or nothing.
   Worlds matter to villages too (villages live in many of GoodeCraft's worlds, and some worlds have locked clocks that pause village
   days), so a shared view of worlds and villages may be useful later; we would shape that together.

3. **The order.** Hamletfolk finishes its own dashboard and tests it locally first. The remote access (1) and the extension point (2)
   come after, and only if Owen agrees. We would send the interface for review before building anything against it.

## Questions for Owen

- Is an admin web page acceptable on a youth server at all, and if so, behind Cloudflare Access with which admins?
- Would GoodeCraft want a panel, and which controls first?
- Any constraints from the server (ports in use, NGINX, memory) we should respect?
