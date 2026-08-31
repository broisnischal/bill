package np.bill.device

import android.content.Context
import android.provider.ContactsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The phone's own contacts, for filling in a customer.
 *
 * A shopkeeper's regulars are already in their phone. Reading them beats retyping a name
 * and a number that are three metres away in another app. Nothing is written back and
 * nothing is uploaded until the shopkeeper picks a specific person.
 */
@Singleton
class Contacts @Inject constructor(@ApplicationContext private val context: Context) {

  data class Entry(val name: String, val phone: String)

  suspend fun load(query: String = ""): List<Entry> = withContext(Dispatchers.IO) {
    val projection = arrayOf(
      ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
      ContactsContract.CommonDataKinds.Phone.NUMBER,
    )

    val selection = if (query.isBlank()) {
      null
    } else {
      "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
    }
    val args = if (query.isBlank()) null else arrayOf("%$query%")

    val seen = HashSet<String>()
    val entries = ArrayList<Entry>()

    runCatching {
      context.contentResolver.query(
        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
        projection,
        selection,
        args,
        "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC",
      )?.use { cursor ->
        val nameColumn = 0
        val numberColumn = 1
        while (cursor.moveToNext() && entries.size < LIMIT) {
          val name = cursor.getString(nameColumn)?.trim().orEmpty()
          val raw = cursor.getString(numberColumn)?.trim().orEmpty()
          if (name.isEmpty() || raw.isEmpty()) continue

          val phone = normalise(raw)
          // One row per person: a contact with three numbers should not appear three times.
          if (!seen.add("$name|$phone")) continue
          entries += Entry(name, phone)
        }
      }
    }

    entries
  }

  /** Nepali mobiles as ten digits, so a contact matches a customer already on file. */
  private fun normalise(raw: String): String {
    val digits = raw.filter(Char::isDigit)
    val national = (if (digits.startsWith("977")) digits.drop(3) else digits).trimStart('0')
    return if (national.length == 10) national else raw
  }

  private companion object {
    const val LIMIT = 500
  }
}
