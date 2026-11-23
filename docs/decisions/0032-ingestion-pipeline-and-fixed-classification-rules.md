# 0032. Ingestion pipeline conventions and fixed classification rules

- **Status:** Proposed
- **Date:** 2026-11-21

## Context

`IFileStorage` exposes stream reading, existence checks, listing and SHA-256
fingerprinting, but has no dedicated method for file size. Likewise,
`IndexingStep` has no `Clock` dependency in its constructor, and ADR 0016 left
the exact extraction fallback behavior and automatic tag formats for
`MediaTypeRule` and `DateRule` open.

## Decision

We will fix the ingestion pipeline conventions and version 1.0 classification
rules as follows:

- `ExtractionStep` infers `MediaType` from the file extension (throwing
  `FileRejected` for unsupported extensions), computes `sizeBytes` from
  extracted metadata (`"sizeBytes"` or `"size"`) or by counting bytes from
  `IFileStorage.read(path)`, parses `duration` from `"duration"` metadata
  (numeric seconds or ISO-8601 duration, defaulting to `Duration.ZERO`), and
  stamps `ingestedAt` with `clock.instant()`.
- `IndexingStep` passes `ctx.file.toSnapshot().ingestedAt()` as the `now`
  instant to `CatalogIndex.ingest` and `applyClassification`, avoiding an extra
  `Clock` dependency on the step.
- `MediaTypeRule` applies to every file and produces one automatic tag equal to
  the lowercase `MediaType` name (`"video"`, `"audio"`, `"image"`, `"document"`).
- `DateRule` applies only when a parseable capture date exists in metadata under
  `"captureDate"`, `"creation_time"` or `"date"` (checked in that order), and
  produces two automatic tags: `"YYYY"` and `"YYYY-MM"`.

## Consequences

- Ingestion works identically against local disk storage and in-memory test
  doubles without modifying the `IFileStorage` port.
- `DateRule.appliesTo` represents a meaningful guard instead of matching every
  file, and generated automatic tags stay predictable for filtering and search.
- Adding support for new file extensions requires updating the extension map in
  `ExtractionStep`, and `MetadataAdapter` should emit the agreed metadata keys
  when available.
