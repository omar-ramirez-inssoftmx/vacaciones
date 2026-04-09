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

      showLoader();
    });
  });
});