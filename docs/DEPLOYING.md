# Deploying

## Pin-and-verify

Every deploy to a real server picks a specific commit — never "whatever's on the branch
right now." That commit either was already verified, or gets verified before it goes live.

1. **Pick a commit** on `claude/minecraft-npc-settlement-mod-h6nhem` (or the branch you're
   working from). Usually the latest one where CI is green.
2. **Verify it**, if it hasn't been already:
   - `./gradlew build` passes.
   - The scenarios in [docs/TESTING.md](TESTING.md) that apply to what changed pass on
     `./gradlew runServer`.
3. **Record it** in [PRODUCTION.md](PRODUCTION.md): the commit hash, the date, and who/what
   verified it.
4. **Deploy that exact jar** — the one CI built for that commit (the `Hamletfolk-plugin`
   artifact on its GitHub Actions run), not a fresh local build that might not match.
5. **Update PRODUCTION.md's "live" line** once it's actually running on the server.

## Before every deploy

Check [PRODUCTION.md](PRODUCTION.md) first:

- If the commit you're about to deploy is already the one recorded as **verified**, skip to
  step 4 above.
- If it's a newer commit than the one recorded as verified, go through steps 2-3 first. Don't
  deploy a commit that's never been verified, even if CI is green — CI proves it builds and
  the unit tests pass, not that it behaves correctly in a real server.

## Rolling back

If something goes wrong after a deploy:

1. Stop the server.
2. Replace the plugin jar with the one built for the previously-**live** commit in
   PRODUCTION.md.
3. If `settlements.json` was written by the newer version and might not load cleanly with the
   older one, restore the matching backup from `plugins/Hamletfolk/backups/` instead (R1.3),
   or check R1.13's migration guarantees before assuming it's fine either way.
4. Update PRODUCTION.md's "live" line back down.
5. File what went wrong as a roadmap item before trying the deploy again.
