/*
 * Mermaid initialization for MkDocs Material.
 *
 * We render Mermaid on the client so diagrams work everywhere the static site is hosted.
 * The docs use ```mermaid fences which are emitted as <div class="mermaid">...</div>
 * via pymdownx.superfences.fence_div_format.
 */

(function () {
  function currentMermaidTheme() {
    // MkDocs Material uses data-md-color-scheme="default" | "slate".
    var scheme = document.body.getAttribute("data-md-color-scheme") || "default";
    return scheme === "slate" ? "dark" : "default";
  }

  function initMermaid() {
    if (!window.mermaid) {
      return;
    }

    // Avoid re-initializing with conflicting config.
    try {
      window.mermaid.initialize({
        startOnLoad: false,
        securityLevel: "strict",
        theme: currentMermaidTheme(),
      });

      // Mermaid v10+ prefers mermaid.run().
      window.mermaid.run({
        querySelector: ".mermaid",
      });
    } catch (e) {
      // If Mermaid throws (e.g., due to malformed diagram), don’t break page rendering.
      // Users will still see the diagram source.
      // eslint-disable-next-line no-console
      console.warn("Mermaid rendering failed", e);
    }
  }

  // Initial render
  document.addEventListener("DOMContentLoaded", initMermaid);

  // Re-render on palette (light/dark) toggle.
  // Material emits changes via the palette component; easiest/most reliable is a page reload.
  document.addEventListener("DOMContentLoaded", function () {
    var palette = document.querySelector("[data-md-component='palette']");
    if (!palette) {
      return;
    }

    palette.addEventListener("change", function () {
      // Full reload guarantees the theme attribute is updated before Mermaid runs.
      window.location.reload();
    });
  });
})();
