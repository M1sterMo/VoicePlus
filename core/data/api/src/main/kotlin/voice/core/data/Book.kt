package voice.core.data

public data class Book(
  val content: BookContent,
  val chapters: List<Chapter>,
) {

  val id: BookId = content.id

  init {
    check(chapters.size == content.chapters.size) {
      "Different chapter count in $this"
    }
    check(chapters.map { it.id } == content.chapters) {
      "Different chapter order in $this"
    }
  }

  val currentChapter: Chapter = chapters[content.currentChapterIndex]
  val currentMark: ChapterMark = currentChapter.markForPosition(content.positionInChapter)

  val position: Long = chapters.takeWhile { it.id != content.currentChapter }
    .sumOf { it.duration } + content.positionInChapter
  val duration: Long = chapters.sumOf { it.duration }

  public inline fun update(update: (BookContent) -> BookContent): Book {
    return copy(content = update(content))
  }

  public fun withElapsedPosition(
    elapsedTime: Long,
    playbackSpeed: Float,
  ): Book {
    val addedAudioTime = (elapsedTime * playbackSpeed).toLong()
    var remainingPositionInChapter = content.positionInChapter + addedAudioTime
    for (index in content.currentChapterIndex until chapters.size) {
      val chapter = chapters[index]
      if (remainingPositionInChapter < chapter.duration || index == chapters.lastIndex) {
        return update {
          it.copy(
            currentChapter = chapter.id,
            positionInChapter = remainingPositionInChapter.coerceAtMost(chapter.duration),
          )
        }
      }
      remainingPositionInChapter -= chapter.duration
    }

    return this
  }
}

/**
 * Wall-clock listening time left at this book's playback speed, not raw audio time —
 * at 2x, a 2h remainder is one real hour (GitHub issue #6).
 */
public fun Book.realTimeRemainingMs(): Long {
  val remaining = (duration - position).coerceAtLeast(0)
  val speed = content.playbackSpeed
  return if (speed > 0f) (remaining / speed.toDouble()).toLong() else remaining
}
