// 663C OBS-before step 2: 01-discover-task.cjs got 404 for the 663A task (that stack was not this
// one), so list Alice's My Work tasks (read-only GET, her own token) and print a few with their
// matter ids, for the K-24 task-status Select. Creates nothing.
const C = require("../../../661B/walk/scripts/common.cjs");
(async () => {
  const t = await C.idpToken("alice");
  const j = await C.api(t, "/api/my-work/tasks");
  const all = [...(j.assigned || []), ...(j.unassigned || [])];
  console.log(JSON.stringify({ assigned: (j.assigned || []).length, unassigned: (j.unassigned || []).length, sample: all.slice(0, 5).map((x) => ({ id: x.id, title: x.title, status: x.status, projectId: x.projectId, projectName: x.projectName })) }));
})().catch((e) => { console.error(e.message); process.exit(1); });
