/**
 * Phones show tables as cards (see index.css). Each cell gets its column title in data-label,
 * filled in automatically for every .data-table / .desk-table, including ones rendered later.
 */
function label(table) {
  const heads = [...table.querySelectorAll('thead th')].map((th) => th.textContent.trim());
  if (!heads.length) return;
  table.querySelectorAll('tbody tr').forEach((tr) => {
    let col = 0;
    [...tr.children].forEach((td) => {
      const text = heads[col] || '';
      if (td.getAttribute('data-label') !== text) td.setAttribute('data-label', text);
      col += Number(td.getAttribute('colspan')) || 1;
    });
  });
}

export function startMobileTables() {
  const run = () => document.querySelectorAll('table.data-table, table.desk-table').forEach(label);
  let queued = false;
  const observer = new MutationObserver(() => {
    if (queued) return;
    queued = true;
    requestAnimationFrame(() => {
      queued = false;
      run();
    });
  });
  observer.observe(document.body, { childList: true, subtree: true });
  run();
}
