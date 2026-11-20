package co.omnistream.domain.port.dto;

/** Data transfer object of the ports. */
public record StreamProfile(String codec, int bitrateKbps, String resolution) {
  public boolean isDirectPlay() {
    throw new UnsupportedOperationException("Not implemented yet");
  }
}
