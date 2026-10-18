/*
 * Recorrido inicial (onboarding) para usuarios nuevos.
 * Los pasos se generan en backend según el rol y se entregan en
 * #tourConfig[data-steps].
 */
(function () {
  "use strict";

  const config = document.getElementById("tourConfig");
  if (!config) {
    return;
  }

  let steps = [];
  try {
    steps = JSON.parse(config.dataset.steps || "[]");
  } catch (error) {
    steps = [];
  }

  const completed = config.dataset.completed === "true";
  const startButton = document.getElementById("startTourButton");
  let index = 0;
  let highlight = null;
  let card = null;
  let currentTarget = null;

  function ensureElements() {
    if (card) {
      return;
    }

    highlight = document.createElement("div");
    highlight.className = "tour-highlight";
    highlight.style.display = "none";
    document.body.appendChild(highlight);

    card = document.createElement("div");
    card.className = "tour-card";
    card.setAttribute("role", "dialog");
    card.setAttribute("aria-modal", "true");
    card.setAttribute("aria-live", "polite");
    document.body.appendChild(card);
  }

  function removeElements() {
    if (highlight) {
      highlight.remove();
      highlight = null;
    }
    if (card) {
      card.remove();
      card = null;
    }
    currentTarget = null;
    document.body.classList.remove("tour-open");
  }

  function startTour() {
    if (!steps.length) {
      return;
    }
    removeElements();
    ensureElements();
    document.body.classList.add("tour-open");
    index = 0;
    render();
  }

  function finish(markCompleted) {
    removeElements();
    if (markCompleted) {
      fetch("/onboarding/complete", {
        method: "POST",
        headers: { "X-Requested-With": "XMLHttpRequest" }
      }).catch(function () {
        // Si falla, el botón "Ver recorrido" sigue disponible.
      });
    }
  }

  function render() {
    const step = steps[index];
    if (!step) {
      finish(true);
      return;
    }

    card.innerHTML = "";
    card.classList.remove("tour-card--center");

    const stepLabel = document.createElement("div");
    stepLabel.className = "tour-card__step";
    stepLabel.textContent = "Paso " + (index + 1) + " de " + steps.length;

    const title = document.createElement("h5");
    title.textContent = step.title || "";

    const description = document.createElement("p");
    description.textContent = step.description || "";

    const actions = document.createElement("div");
    actions.className = "tour-card__actions";

    const skip = document.createElement("button");
    skip.type = "button";
    skip.className = "tour-btn tour-btn--link";
    skip.textContent = "Omitir";
    skip.addEventListener("click", function () {
      finish(true);
    });

    const previous = document.createElement("button");
    previous.type = "button";
    previous.className = "tour-btn tour-btn--ghost";
    previous.textContent = "Anterior";
    previous.disabled = index === 0;
    previous.addEventListener("click", function () {
      if (index > 0) {
        index -= 1;
        render();
      }
    });

    const next = document.createElement("button");
    next.type = "button";
    next.className = "tour-btn tour-btn--primary";
    next.textContent = index === steps.length - 1 ? "Finalizar" : "Siguiente";
    next.addEventListener("click", function () {
      if (index === steps.length - 1) {
        finish(true);
      } else {
        index += 1;
        render();
      }
    });

    actions.appendChild(skip);
    actions.appendChild(previous);
    actions.appendChild(next);

    card.appendChild(stepLabel);
    card.appendChild(title);
    card.appendChild(description);
    card.appendChild(actions);

    const hint = document.createElement("div");
    hint.className = "tour-card__hint";
    hint.textContent = "Puedes cerrar con Esc";
    card.appendChild(hint);

    positionCard(step);
  }

  function positionCard(step) {
    highlight.style.display = "none";
    currentTarget = null;

    if (!step.target) {
      card.classList.add("tour-card--center");
      return;
    }

    const target = document.querySelector(step.target);
    if (!target) {
      card.classList.add("tour-card--center");
      return;
    }

    currentTarget = target;
    target.scrollIntoView({ behavior: "smooth", block: "center" });
    window.setTimeout(function () {
      if (currentTarget === target) {
        drawHighlight(target);
      }
    }, 220);
  }

  function drawHighlight(target) {
    const rect = target.getBoundingClientRect();
    const padding = 6;
    highlight.style.display = "block";
    highlight.style.top = Math.max(8, rect.top - padding) + "px";
    highlight.style.left = Math.max(8, rect.left - padding) + "px";
    highlight.style.width = Math.min(window.innerWidth - 16, rect.width + padding * 2) + "px";
    highlight.style.height = Math.min(window.innerHeight - 16, rect.height + padding * 2) + "px";
  }

  function redraw() {
    if (currentTarget && highlight && highlight.style.display !== "none") {
      drawHighlight(currentTarget);
    }
  }

  window.addEventListener("resize", redraw);
  window.addEventListener("scroll", redraw, { passive: true });
  window.addEventListener("keydown", function (event) {
    if (!card) {
      return;
    }
    if (event.key === "Escape") {
      finish(true);
    } else if (event.key === "ArrowRight") {
      if (index < steps.length - 1) {
        index += 1;
        render();
      }
    } else if (event.key === "ArrowLeft" && index > 0) {
      index -= 1;
      render();
    }
  });

  if (startButton) {
    startButton.addEventListener("click", startTour);
  }

  if (!completed && steps.length > 0) {
    window.setTimeout(startTour, 600);
  }
})();
