() => {
  const grid = document.querySelector('[data-testid=diary-week-grid]');
  const cells = [...grid.querySelectorAll('[role=gridcell]')];
  const lefts = [...new Set(cells.map(c => Math.round(c.getBoundingClientRect().left)))];
  const ev = grid.querySelector('a[data-testid^=diary-event-]') || grid.querySelector('[data-testid^=diary-event-]');
  let evInfo = null;
  if (ev) {
    const textCol = ev.querySelector('span.min-w-0.flex-1');
    const inner = textCol.querySelector('span');
    const kids = [...inner.children];
    const time = kids.find(k => k.className.includes('font-mono'));
    const title = kids.find(k => k.className.includes('line-clamp-2'));
    const lh = parseFloat(getComputedStyle(title).lineHeight);
    const tr = title.getBoundingClientRect(), tm = time && time.getBoundingClientRect();
    evInfo = { titleAttr: ev.getAttribute('title'), titleText: title.innerText, textColW: Math.round(textCol.getBoundingClientRect().width),
      titleW: Math.round(tr.width), titleH: Math.round(tr.height), lines: Math.round(tr.height / lh), titleScrollH: title.scrollHeight,
      timeText: time && time.innerText, timeAbove: tm ? tm.bottom <= tr.top + 1 : null, timeClipped: time ? time.scrollWidth > time.clientWidth : null,
      cell: ev.closest('[role=gridcell]').dataset.testid };
  }
  return { iw: innerWidth, sw: document.documentElement.scrollWidth, gridW: Math.round(grid.getBoundingClientRect().width),
    containerW: Math.round(grid.parentElement.getBoundingClientRect().width), columns: lefts.length, cellW: Math.round(cells[0].getBoundingClientRect().width), ev: evInfo };
}
