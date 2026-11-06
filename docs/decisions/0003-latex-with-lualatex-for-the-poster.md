# 0003. LaTeX with LuaLaTeX for the poster

- **Status:** Accepted
- **Date:** 2026-11-05

## Context

The enterprise architecture is delivered as a poster of three 58.8 x 33 cm
boards, built from a LaTeX template provided for the course. Several people edit
it at the same time, and the result must be reproducible from the sources.

## Decision

We will keep the poster in LaTeX, compiled with LuaLaTeX through `latexmk`, and
bibliography handled by biblatex and biber. Fonts come from TeX Live
(Montserrat for headings, Lato for body text), so nothing has to be installed
separately. The random seed used by the hand-drawn line effect is fixed
(`\pgfmathsetseed{2024}`) so each build produces the same PDF.

## Consequences

- The poster is plain text: it can be versioned, diffed and reviewed like code.
- It compiles the same way locally, in CI and in Overleaf.
- Building locally needs a full TeX distribution (TeX Live, MiKTeX or MacTeX).
- Compiling with `lualatex` alone is not enough; `latexmk` is required (see ADR 0004).
