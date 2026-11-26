// 663C OBS-before step 1: read-only GET (Alice's own token) of the 663A task's matter id, for the
// K-24 task-status Select (the only Select with item icons, task-status-select.tsx). Creates nothing.
const C = require("../../../661B/walk/scripts/common.cjs");
(async () => {
  const t = await C.idpToken("alice");
  const task = await C.api(t, "/api/tasks/cb6f89af-638c-4233-837b-a6a03cb00fe7");
  console.log(JSON.stringify({ taskId: task.id, title: task.title, status: task.status, projectId: task.projectId || task.matterId }));
})().catch((e) => { console.error(e.message); process.exit(1); });
