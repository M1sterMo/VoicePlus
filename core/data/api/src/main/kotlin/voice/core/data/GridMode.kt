package voice.core.data

import kotlinx.serialization.Serializable

@Serializable
public enum class GridMode {
  LIST,
  GRID,
  BOOKS,
  FOLLOW_DEVICE,
}

/** Whether the library draws book jackets: in Books, and by default, which also shows Books. */
public val GridMode.isBooks: Boolean
  get() = this == GridMode.BOOKS || this == GridMode.FOLLOW_DEVICE
