/*
 * Aplica el tema antes de pintar para evitar destellos al cargar.
 * Prioridad: preferencia guardada en el servidor (data-theme) > localStorage > oscuro.
 */
(function () {
  try {
    var stored = localStorage.getItem("inssoft-theme");
    var server = document.documentElement.getAttribute("data-theme");
    var theme = server || stored || "DARK";
    document.documentElement.setAttribute("data-theme", theme === "LIGHT" ? "LIGHT" : "DARK");
  } catch (e) {
    document.documentElement.setAttribute("data-theme", "DARK");
  }
})();
