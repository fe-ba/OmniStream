package co.omnistream.domain.event;

/** Domain event. */
public final class FileIngested extends CatalogEvent {
  @Override
  public ChangeType changeType() {
    throw new UnsupportedOperationException("Not implemented yet");
  }
}
