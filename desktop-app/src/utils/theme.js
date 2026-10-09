import { useEffect, useState } from 'react';

const KEY = 'dokan_theme';

function readTheme() {
  try {
    const t = localStorage.getItem(KEY);
    if (t === 'light' || t === 'dark') return t;
  } catch {
    /* storage blocked */
  }
  return window.matchMedia?.('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
}

// Apply before React renders so there is no flash of the wrong theme.
export function applyInitialTheme() {
  document.documentElement.dataset.theme = readTheme();
}

export function useTheme() {
  const [theme, setTheme] = useState(readTheme);
  useEffect(() => {
    document.documentElement.dataset.theme = theme;
  }, [theme]);
  const toggle = () => {
    const next = theme === 'dark' ? 'light' : 'dark';
    try {
      localStorage.setItem(KEY, next);
    } catch {
      /* storage blocked */
    }
    setTheme(next);
  };
  return { theme, toggle };
}
