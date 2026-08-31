package np.bill.core.geo

/**
 * Nepal's provinces and districts.
 *
 * Addresses on a bill are typed once and read by a tax officer, so a district picked from
 * a list beats one typed at a counter: "Kathmandu", "Kathamandu" and "KTM" all read the
 * same to the person entering them and differently to anyone filtering later.
 *
 * The seven provinces and seventy-seven districts are fixed by the constitution. Local
 * levels (municipalities and rural municipalities) are not listed here, because there are
 * 753 of them and a partial list would be worse than a free-text field; that one stays
 * typed, with the phone's own location offering a suggestion.
 */
object Nepal {

  data class Province(val name: String, val nameNepali: String, val districts: List<String>)

  val provinces: List<Province> = listOf(
    Province(
      "Koshi", "कोशी",
      listOf(
        "Bhojpur", "Dhankuta", "Ilam", "Jhapa", "Khotang", "Morang", "Okhaldhunga",
        "Panchthar", "Sankhuwasabha", "Solukhumbu", "Sunsari", "Taplejung", "Terhathum",
        "Udayapur",
      ),
    ),
    Province(
      "Madhesh", "मधेश",
      listOf(
        "Bara", "Dhanusha", "Mahottari", "Parsa", "Rautahat", "Saptari", "Sarlahi", "Siraha",
      ),
    ),
    Province(
      "Bagmati", "बागमती",
      listOf(
        "Bhaktapur", "Chitwan", "Dhading", "Dolakha", "Kathmandu", "Kavrepalanchok",
        "Lalitpur", "Makwanpur", "Nuwakot", "Ramechhap", "Rasuwa", "Sindhuli",
        "Sindhupalchok",
      ),
    ),
    Province(
      "Gandaki", "गण्डकी",
      listOf(
        "Baglung", "Gorkha", "Kaski", "Lamjung", "Manang", "Mustang", "Myagdi", "Nawalpur",
        "Parbat", "Syangja", "Tanahun",
      ),
    ),
    Province(
      "Lumbini", "लुम्बिनी",
      listOf(
        "Arghakhanchi", "Banke", "Bardiya", "Dang", "Eastern Rukum", "Gulmi", "Kapilvastu",
        "Palpa", "Parasi", "Pyuthan", "Rolpa", "Rupandehi",
      ),
    ),
    Province(
      "Karnali", "कर्णाली",
      listOf(
        "Dailekh", "Dolpa", "Humla", "Jajarkot", "Jumla", "Kalikot", "Mugu", "Salyan",
        "Surkhet", "Western Rukum",
      ),
    ),
    Province(
      "Sudurpashchim", "सुदूरपश्चिम",
      listOf(
        "Achham", "Baitadi", "Bajhang", "Bajura", "Dadeldhura", "Darchula", "Doti",
        "Kailali", "Kanchanpur",
      ),
    ),
  )

  val provinceNames: List<String> = provinces.map(Province::name)

  fun districtsOf(province: String?): List<String> =
    provinces.firstOrNull { it.name == province }?.districts ?: provinces.flatMap(Province::districts).sorted()

  /** Which province a district belongs to, so picking a district alone still fills both. */
  fun provinceOf(district: String?): String? {
    if (district.isNullOrBlank()) return null
    return provinces.firstOrNull { province -> province.districts.any { it.equals(district, true) } }?.name
  }

  /** Matches whatever a geocoder handed back to a district we know, or null. */
  fun matchDistrict(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val cleaned = raw.trim().removeSuffix(" District").removeSuffix(" district").trim()
    return provinces
      .flatMap(Province::districts)
      .firstOrNull { it.equals(cleaned, ignoreCase = true) }
  }

  /** Wards run from 1; the largest metropolitan cities go to 32. */
  val wards: List<Int> = (1..35).toList()
}
