package co.omnistream.domain.model.catalog;

/** Kind of content of a media file, decided when the file is ingested. */
public enum MediaType {
  /** Video, with or without audio. */
  VIDEO,
  /** Audio only. */
  AUDIO,
  /** Still image. */
  IMAGE,
  /** Text document, such as a PDF or an e-book. */
  DOCUMENT
}
