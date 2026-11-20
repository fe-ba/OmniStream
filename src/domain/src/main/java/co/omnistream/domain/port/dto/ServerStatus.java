package co.omnistream.domain.port.dto;

/** Data transfer object of the ports. */
public record ServerStatus(int indexedFiles, long freeSpace, int activeSessions) {}
