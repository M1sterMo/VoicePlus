package voice.core.scanner

import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.nio.charset.Charset

@RunWith(JUnit4::class)
class LegacyTagTextTest {

  @Test
  fun `repairs windows-1251 written into a frame that declares latin-1`() {
    listOf(
      "Валис",
      "Ф. Дик",
      "Часть 1",
      "Ёлка",
      "Гарри Поттер и философский камень",
      "Валис (MP3)",
    ).forEach { title ->
      repairLegacyTagText(mislabelled(title, WINDOWS_1251)) shouldBe title
    }
  }

  @Test
  fun `repairs utf-8 written into a frame that declares latin-1, in any script`() {
    listOf(
      "Валис",
      "Grüße",
      "Οδυσσέας",
      "Claude Shannon, père de l'information",
      "日本語",
      "שלום",
    ).forEach { title ->
      repairLegacyTagText(mislabelled(title, Charsets.UTF_8)) shouldBe title
    }
  }

  @Test
  fun `repairs windows-1252 punctuation that arrived as control characters`() {
    listOf(
      "Zürich — Teil 1",
      "L’été, c’est ça",
      "Café — 5 €",
    ).forEach { title ->
      repairLegacyTagText(mislabelled(title, WINDOWS_1252)) shouldBe title
    }
  }

  @Test
  fun `leaves correct latin-1 text alone`() {
    listOf(
      "Café",
      "Müller",
      "Ação",
      "Señor",
      "Garçon",
      "Naïve",
      "Tôi",
      "ÉTÉ",
      "Chapter One",
      "01 - Chapter One",
    ).forEach { title ->
      repairLegacyTagText(title) shouldBe title
    }
  }

  @Test
  fun `leaves text that came out of a utf-16 frame alone`() {
    listOf(
      "Валис",
      "日本語",
      "Łódź",
      "İstanbul",
      "Ελλάδα",
    ).forEach { title ->
      repairLegacyTagText(title) shouldBe title
    }
  }

  @Test
  fun `leaves text it has no evidence about alone`() {
    // Two letters are a coincidence as often as a repair, so "Да" stays as it came in rather than becoming
    // something that merely looks Cyrillic.
    repairLegacyTagText(mislabelled("Да", WINDOWS_1251)) shouldBe "Äà"
    repairLegacyTagText("") shouldBe ""
    repairLegacyTagText("   ") shouldBe "   "
  }

  @Test
  fun `repairing twice changes nothing`() {
    val broken = mislabelled("Валис", WINDOWS_1251)
    val repaired = repairLegacyTagText(broken)

    repairLegacyTagText(repaired) shouldBe repaired
  }

  private companion object {
    private val WINDOWS_1251: Charset = Charset.forName("windows-1251")
    private val WINDOWS_1252: Charset = Charset.forName("windows-1252")

    /** The string Media3 hands over for a frame that declares ISO-8859-1 but holds [text] as [charset]. */
    private fun mislabelled(
      text: String,
      charset: Charset,
    ): String {
      return text.toByteArray(charset).toString(Charsets.ISO_8859_1)
    }
  }
}
