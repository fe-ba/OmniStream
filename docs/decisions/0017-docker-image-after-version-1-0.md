# 0017. Docker image after version 1.0

- **Status:** Accepted
- **Date:** 2026-11-19

## Context

The technology layer of the enterprise architecture deploys OmniStream as a
Docker image (`omnistream:latest`) on a Docker runtime, on the server or NAS
where it is self-hosted, and distributes it through Docker Hub. The software
architecture said nothing about how the server is packaged.

Version 1.0 has to be finished within the semester, and an image adds work that
does not show any part of the design: a Dockerfile, the external tools
(FFmpeg, FFprobe, ExifTool) inside the image, volumes for the library and the
database, and publishing.

## Decision

Version 1.0 will not have a Docker image. It runs from the build output of the
`omnistream-bootstrap` module (ADR 0013) with Java 25, on a machine that has
the external tools installed.

The Docker image is planned for a later version. When it is built:

- the image will contain the server and the external tools it calls;
- the library folder, the database and the configuration will be volumes, so
  the container can be replaced without losing data;
- the profile will be selected by configuration, as it is now.

## Consequences

- Version 1.0 focuses on the architecture and its patterns.
- Installing version 1.0 requires Java 25 and the external tools on the
  machine; the README must explain how.
- The image needs no change in the code: the composition root already reads the
  paths and the profile from the configuration.
- Until the image exists, the deployment shown in the enterprise architecture
  is ahead of the product.
