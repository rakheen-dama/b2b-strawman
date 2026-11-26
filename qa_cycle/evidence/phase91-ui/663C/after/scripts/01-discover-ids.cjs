// 663C OBS-after step 1 (NEW, replaces before's 01-discover-task.cjs): the stack was rebuilt, so the
// before ids (INV-0005 fbf447d0-…, matter "Team spec mwxbfat81651" 361f4148-…) are gone. Re-discover,
// read-only GETs with Alice's own token: the INV-0005 (SENT) fee note, as before, and a "Team spec"
// matter with tasks for the K-24 task-status Select (before picked one with 9 tasks), and (after-only)
// a document template id for K-26's long-chain crumb page /settings/documents/templates/[id]/edit. Creates nothing.
const C = require("../../../661B/walk/scripts/common.cjs");
(async () => {
  const t = await C.idpToken("alice");
  const inv = C.list(await C.api(t, "/api/invoices?size=100"));
  const feeNote = inv.find((i) => i.invoiceNumber === "INV-0005");
  const out = { feeNote: feeNote && { id: feeNote.id, number: feeNote.invoiceNumber, status: feeNote.status } };
  for (const p of ["/api/matters?size=200", "/api/projects"]) {
    try {
      const ms = C.list(await C.api(t, p));
      const spec = ms.filter((m) => /team spec/i.test(m.name));
      out.mattersEndpoint = p;
      out.matterCount = ms.length;
      out.teamSpec = [];
      for (const m of spec.slice(0, 6)) {
        let n = null;
        for (const tp of [`/api/matters/${m.id}/tasks`, `/api/tasks?projectId=${m.id}`]) { try { n = C.list(await C.api(t, tp)).length; break; } catch (e) { n = e.message; } }
        out.teamSpec.push({ id: m.id, name: m.name, tasks: n });
      }
      break;
    } catch (e) { out[p] = e.message; }
  }
  const tpl = C.list(await C.api(t, "/api/templates")).sort((a, b) => (b.name || "").length - (a.name || "").length);
  out.templates = tpl.length;
  out.longestTemplate = tpl[0] && { id: tpl[0].id, name: tpl[0].name };
  console.log(JSON.stringify(out));
})().catch((e) => { console.error(e.message); process.exit(1); });
