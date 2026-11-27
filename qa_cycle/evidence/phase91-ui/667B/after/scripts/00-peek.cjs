// OBS-after read-only peek: the investments (all fields) on the primary account, and /maturing.
const L = require("../../before/scripts/lib.cjs");
(async () => {
  const S = L.readSeed(); const t = await L.idpToken("alice");
  const inv = await L.apiGet(t, `/api/trust-accounts/${S.ids.accountId}/investments?page=0&size=100&sort=depositDate,desc`);
  console.log(JSON.stringify(L.list(inv.json)));
  console.log(JSON.stringify((await L.apiGet(t, `/api/trust-accounts/${S.ids.accountId}/investments/maturing`)).json));
  console.log(JSON.stringify(S.accounts || S.account));
})();
