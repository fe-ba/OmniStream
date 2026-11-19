# 0016. Automatic classification

- **Status:** Accepted
- **Date:** 2026-11-19

## Context

The enterprise architecture asks for automatic classification: "Clasificación
automática" and "Organización automática" are requirements of the motivation
layer, and the first specific objective asks for categorization that adapts to
the criteria of each user or organization. The software design extracted
metadata and detected duplicates on ingestion, but nothing assigned tags or
collections by itself.

The project layer of the poster puts an "Automatización" gap between the
OmniStream 1.0 and 2.0 plateaus, so part of this can wait. Version 1.0 still
has to show the mechanism working.

OmniStream is offline and independent of external services, so classification
cannot depend on a cloud service or a trained model.

## Decision

We will classify files with rules over data the system already has: media type,
extracted metadata (capture date, camera, duration, resolution, codec) and the
source folder.

- A new step of the ingestion chain, `ClassificationStep`, runs between
  `DuplicatesStep` and `IndexingStep`. Reindexing uses the same chain, so it
  also reclassifies.
- Each rule is a strategy (`ClassificationRule`): it says whether it applies to
  a file and returns a `Classification` (tags and collections). The step
  combines the results of all the rules.
- `CatalogIndex.applyClassification()` replaces only the file's automatic tags
  and adds it to the given collections. `Tag` now has a source (`AUTOMATIC` or
  `MANUAL`), so tags set by the user are never overwritten. The aggregate
  records `FileClassified`, which reaches synchronization through the outbox
  like any other catalog event.

Version 1.0 includes the step and two fixed rules: `MediaTypeRule` and
`DateRule`.

Version 2.0 adds rules defined by the administrator, and these parts are drawn
dashed in the diagrams:

- `ClassificationCriterion`: a field, an operator, a value, and the tag or
  collection it leads to;
- the `IRuleManagement` driving port and `ClassificationService` to create,
  list and delete rules and to reclassify the library;
- the `IRuleRepository` driven port, implemented by the JDBC adapter with
  `CriterionMapper`;
- `UserDefinedRules`: a rule that applies the stored criteria. It is added to
  the list of rules, so neither the chain nor the other rules change.

## Consequences

- The requirements of the enterprise architecture on automatic classification
  have a place in the design, and version 1.0 shows the Chain of Responsibility
  and Strategy patterns working on them.
- Adding a rule means writing one class or, from 2.0 on, storing one
  criterion. No other code changes.
- Classification works offline and gives the same result every time.
- Rules can only use data the system already extracts. Classifying by image
  content (faces, objects) is not covered.
- The diagrams change in both languages (ADR 0008): domain model, services,
  ports and adapters, components and use cases.
