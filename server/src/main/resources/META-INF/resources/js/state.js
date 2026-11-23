// Selected child, remembered per browser.
const KEY = 'jlibrus.child';

export const state = {
  children: [],
  childId: null
};

export function selectChild(id) {
  state.childId = id;
  try {
    localStorage.setItem(KEY, id);
  } catch (e) {
    // storage unavailable: selection lasts for this page only
  }
}

export function restoreChild() {
  let saved = null;
  try {
    saved = localStorage.getItem(KEY);
  } catch (e) {
    saved = null;
  }
  const known = state.children.find((c) => c.id === saved);
  state.childId = known ? known.id : (state.children[0] ? state.children[0].id : null);
}

export function childPath(suffix) {
  return `/api/children/${encodeURIComponent(state.childId)}${suffix}`;
}
