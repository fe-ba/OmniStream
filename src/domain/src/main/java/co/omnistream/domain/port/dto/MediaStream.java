package co.omnistream.domain.port.dto;

import java.io.InputStream;

/** Data transfer object of the ports. */
public record MediaStream(InputStream data, String mime, boolean transcoded) {}
