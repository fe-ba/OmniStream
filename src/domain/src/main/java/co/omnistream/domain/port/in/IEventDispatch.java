package co.omnistream.domain.port.in;

/** Driving port: implemented by a domain service, called by primary adapters. */
public interface IEventDispatch {
  int publishPending(int limit);
}
