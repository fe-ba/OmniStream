# 0004. Version diagrams as SVG

- **Status:** Accepted
- **Date:** 2026-11-05

## Context

The diagrams are modeled in Archi and draw.io and embedded in the poster. They
were originally stored as PDF, which git cannot diff, and each figure had a
hand-tuned `scale=`, so re-exporting a diagram at a different size made it
overflow its cell. LuaLaTeX cannot read SVG directly.

## Decision

We will version only the SVG export of each diagram, under `imgs/`. Before each
build, `.latexmkrc` converts the SVGs to vector PDFs in `build/svg/` (using
`rsvg-convert`, `inkscape` or `cairosvg`, whichever is installed), reconverting
only the ones that changed. Diagrams are placed with the `\Diagrama` macro,
which scales each figure to fit its cell without distorting it.

## Consequences

- Diagram changes are visible in diffs and the repository stays smaller.
- Re-exporting a diagram never breaks the layout.
- Generated PDFs in `imgs/` are ignored by git.
- Local builds need an SVG converter; without one, diagrams come out blank.
- SVG files are marked `-merge` in `.gitattributes`: concurrent edits are
  resolved by re-exporting from the model, not by merging the XML.
