// 664A OBS-before step 0: read-only discovery (GET only, each persona's own mock-IDP token) of what the
// capture needs: each persona's capabilities (picks Carol's forbidden ?tab=), the org's enabled modules
// (module-gated tabs), a matter id and a client id (grouped-tab-bar baseline). Creates nothing.
const C = require("../../../661B/walk/scripts/common.cjs");
const safe = async (f) => { try { return await f(); } catch (e) { return "ERR " + e.message; } };
(async () => {
  for (const who of ["alice", "carol"]) {
    const t = await C.idpToken(who);
    const out = { who };
    out.caps = await safe(async () => { const j = await C.api(t, "/api/me/capabilities"); return { isAdmin: j.isAdmin, isOwner: j.isOwner, capabilities: j.capabilities }; });
    out.enabledModules = await safe(async () => (await C.api(t, "/api/settings")).enabledModules);
    out.matters = await safe(async () => C.list(await C.api(t, "/api/matters?size=3")).slice(0, 3).map((p) => `${p.id} ${p.name}`));
    out.clients = await safe(async () => C.list(await C.api(t, "/api/clients?size=3")).slice(0, 3).map((c) => `${c.id} ${c.name}`));
    console.log(JSON.stringify(out));
  }
})();
