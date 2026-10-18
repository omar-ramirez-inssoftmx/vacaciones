/*
 * CSRF para fetch: Spring Security expone el token en la cookie XSRF-TOKEN.
 * Cualquier POST/PUT/PATCH/DELETE agrega automáticamente el header X-XSRF-TOKEN.
 */
(function () {
  if (window.__csrfFetchInstalled) {
    return;
  }
  window.__csrfFetchInstalled = true;

  const originalFetch = window.fetch.bind(window);

  window.fetch = function (input, init) {
    const method =
      (init && init.method) ||
      (typeof input === "object" && input.method) ||
      "GET";

    if (!/^(GET|HEAD|OPTIONS|TRACE)$/i.test(method)) {
      const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
      if (match) {
        init = init || {};
        const headers = new Headers(init.headers || {});
        if (!headers.has("X-XSRF-TOKEN")) {
          headers.set("X-XSRF-TOKEN", decodeURIComponent(match[1]));
        }
        init.headers = headers;
      }
    }

    return originalFetch(input, init);
  };
})();

/*
 * Tema claro/oscuro persistido por usuario y por navegador.
 */
(function () {
  if (window.__themeInstalled) {
    return;
  }
  window.__themeInstalled = true;

  const STORAGE_KEY = "inssoft-theme";
  const DEFAULT_THEME = "DARK";

  if (!document.querySelector('link[data-theme-styles]')) {
    const link = document.createElement("link");
    link.rel = "stylesheet";
    link.href = "/css/theme-light.css";
    link.dataset.themeStyles = "true";
    document.head.appendChild(link);
  }

  function normalize(theme) {
    return theme === "LIGHT" ? "LIGHT" : "DARK";
  }

  function currentTheme() {
    return normalize(document.documentElement.getAttribute("data-theme"));
  }

  function updateButton() {
    const button = document.getElementById("themeToggleButton");
    if (!button) {
      return;
    }
    const isDark = currentTheme() === "DARK";
    button.innerHTML = isDark
      ? '<span class="theme-toggle__icon">☀️</span><span class="theme-toggle__label">Modo claro</span>'
      : '<span class="theme-toggle__icon">🌙</span><span class="theme-toggle__label">Modo oscuro</span>';
    button.setAttribute("aria-label", isDark ? "Cambiar a modo claro" : "Cambiar a modo oscuro");
    button.setAttribute("title", isDark ? "Cambiar a modo claro" : "Cambiar a modo oscuro");
  }

  function applyTheme(theme, persist) {
    const value = normalize(theme);
    document.documentElement.setAttribute("data-theme", value);
    if (persist) {
      localStorage.setItem(STORAGE_KEY, value);
    }
    updateButton();
  }

  function createButton() {
    if (document.getElementById("themeToggleButton")) {
      return;
    }
    const button = document.createElement("button");
    button.type = "button";
    button.id = "themeToggleButton";
    button.className = "theme-toggle";
    button.addEventListener("click", function () {
      const next = currentTheme() === "DARK" ? "LIGHT" : "DARK";
      applyTheme(next, true);

      if (!/^\/(login|change-password)/.test(window.location.pathname)) {
        fetch("/profile/theme", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ theme: next })
        }).catch(function () {
          // La preferencia local sigue funcionando aunque falle el guardado.
        });
      }
    });
    document.body.appendChild(button);
    updateButton();
  }

  const serverTheme = document.documentElement.getAttribute("data-theme");
  const storedTheme = localStorage.getItem(STORAGE_KEY);
  applyTheme(serverTheme || storedTheme || DEFAULT_THEME, Boolean(serverTheme));

  if (document.body) {
    createButton();
  } else {
    document.addEventListener("DOMContentLoaded", createButton);
  }

  // Si el usuario aún no tiene preferencia local, recupera la de su cuenta.
  if (!storedTheme && !/^\/(login|change-password)/.test(window.location.pathname)) {
    fetch("/profile/theme", { headers: { "Accept": "application/json" } })
      .then(function (response) { return response.ok ? response.json() : null; })
      .then(function (payload) {
        if (!localStorage.getItem(STORAGE_KEY) && payload && payload.data && payload.data.theme) {
          applyTheme(payload.data.theme, true);
        }
      })
      .catch(function () {
        // Sin sesión o sin preferencia: se conserva el tema local/por defecto.
      });
  }
})();

/*
 * Loader global del sistema.
 *
 * Mejora aplicada:
 * - Delay de 120 ms para evitar parpadeos
 * - Permite excluir elementos con la clase:
 *     no-loader
 *
 * Úsalo en botones o links rápidos como:
 * - Volver
 * - Cerrar modal
 * - Navegación instantánea
 *
 * Uso manual:
 *   window.AppLoader.show();
 *   window.AppLoader.showNow();
 *   window.AppLoader.hide();
 */

document.addEventListener("DOMContentLoaded", () => {
  const loader = document.getElementById("globalLoader");

  if (!loader) {
    return;
  }

  let isVisible = false;
  let showTimer = null;

  /**
   * Muestra el loader inmediatamente.
   */
  function showLoaderNow() {
    if (isVisible) {
      return;
    }

    loader.classList.add("show");
    document.body.classList.add("loading");
    isVisible = true;
  }

  /**
   * Programa el loader con retraso.
   */
  function showLoader() {
    if (isVisible) {
      return;
    }

    if (showTimer) {
      clearTimeout(showTimer);
    }

    showTimer = setTimeout(() => {
      showLoaderNow();
      showTimer = null;
    }, 120);
  }

  /**
   * Oculta el loader y cancela timers pendientes.
   */
  function hideLoader() {
    if (showTimer) {
      clearTimeout(showTimer);
      showTimer = null;
    }

    loader.classList.remove("show");
    document.body.classList.remove("loading");
    isVisible = false;
  }

  window.AppLoader = {
    show: showLoader,
    showNow: showLoaderNow,
    hide: hideLoader
  };

  /*
   * Evita que quede visible al volver con cache del navegador.
   */
  window.addEventListener("pageshow", () => {
    hideLoader();
  });

  /*
   * Formularios:
   * Si el form tiene .no-loader, no se muestra.
   */
  document.querySelectorAll("form").forEach(form => {
    form.addEventListener("submit", () => {
      if (form.classList.contains("no-loader")) {
        return;
      }

      showLoader();
    });
  });

  /*
   * Links internos:
   * Si el link tiene .no-loader, no se muestra.
   */
  document.querySelectorAll("a[href]").forEach(link => {
    link.addEventListener("click", () => {
      const href = link.getAttribute("href");

      if (!href || href.trim() === "") {
        return;
      }

      if (link.classList.contains("no-loader")) {
        return;
      }

      if (href.startsWith("#")) {
        return;
      }

      if (href.startsWith("javascript:")) {
        return;
      }

      if (link.hasAttribute("download")) {
        return;
      }

      if (link.target === "_blank") {
        return;
      }

      const url = new URL(link.href, window.location.origin);

      if (url.origin !== window.location.origin) {
        return;
      }
	  
	  if (href.includes("/descargar/")) {
	      return;
	  }

      showLoader();
    });
  });
});