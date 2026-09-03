package np.bill.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The icon set: Lucide, drawn as strokes.
 *
 * Material's filled icons were the last thing in the app that still looked like a
 * default, and they are the wrong weight beside this type: a solid glyph next to Geist at
 * 15sp reads heavier than the word it labels. These are the same icons the web app draws
 * with, so a shopkeeper who signs a browser in sees the same marks there.
 *
 * Held as path data rather than as drawable resources on purpose. An `ImageVector` is a
 * plain value, so a tab can keep one in a `val` without a composable to load it. The
 * stroke is black and `Icon` tints the whole thing, which is why nothing here carries a
 * colour of its own.
 */
private fun lucide(name: String, vararg paths: String): ImageVector =
  ImageVector.Builder(
    name = name,
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
  ).apply {
    for (data in paths) {
      addPath(
        pathData = addPathNodes(data),
        stroke = SolidColor(Color.Black),
        // Lucide draws at 2. A hair under that sits better beside Geist, which is not a
        // heavy face, and stops a row of icons out-weighing the words next to them.
        strokeLineWidth = 1.8f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
      )
    }
  }.build()

object BillIcons {
  val ArrowLeft = lucide(
    "arrow-left",
    "m12 19-7-7 7-7",
    "M19 12H5",
  )

  val Banknote = lucide(
    "banknote",
    "M4 6h16a2 2 0 0 1 2 2v8a2 2 0 0 1 -2 2h-16a2 2 0 0 1 -2 -2v-8a2 2 0 0 1 2 -2z",
    "M10 12a2 2 0 1 0 4 0a2 2 0 1 0 -4 0",
    "M6 12h.01M18 12h.01",
  )

  val Calculator = lucide(
    "calculator",
    "M6 2h12a2 2 0 0 1 2 2v16a2 2 0 0 1 -2 2h-12a2 2 0 0 1 -2 -2v-16a2 2 0 0 1 2 -2z",
    "M8 6L16 6",
    "M16 14L16 18",
    "M16 10h.01",
    "M12 10h.01",
    "M8 10h.01",
    "M12 14h.01",
    "M8 14h.01",
    "M12 18h.01",
    "M8 18h.01",
  )

  val Camera = lucide(
    "camera",
    "M13.997 4a2 2 0 0 1 1.76 1.05l.486.9A2 2 0 0 0 18.003 7H20a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V9a2 2 0 0 1 2-2h1.997a2 2 0 0 0 1.759-1.048l.489-.904A2 2 0 0 1 10.004 4z",
    "M9 13a3 3 0 1 0 6 0a3 3 0 1 0 -6 0",
  )

  val ChartColumn = lucide(
    "chart-column",
    "M3 3v16a2 2 0 0 0 2 2h16",
    "M18 17V9",
    "M13 17V5",
    "M8 17v-3",
  )

  val Check = lucide(
    "check",
    "M20 6 9 17l-5-5",
  )

  val ChevronDown = lucide(
    "chevron-down",
    "m6 9 6 6 6-6",
  )

  val ChevronLeft = lucide(
    "chevron-left",
    "m15 18-6-6 6-6",
  )

  val ChevronRight = lucide(
    "chevron-right",
    "m9 18 6-6-6-6",
  )

  val ChevronUp = lucide(
    "chevron-up",
    "m18 15-6-6-6 6",
  )

  val CircleAlert = lucide(
    "circle-alert",
    "M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0",
    "M12 8L12 12",
    "M12 16L12.01 16",
  )

  val Clock = lucide(
    "clock",
    "M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0",
    "M12 6v6l4 2",
  )

  val Cloud = lucide(
    "cloud",
    "M17.5 19H9a7 7 0 1 1 6.71-9h1.79a4.5 4.5 0 1 1 0 9Z",
  )

  val CloudCheck = lucide(
    "cloud-check",
    "m17 15-5.5 5.5L9 18",
    "M5.516 16.07A7 7 0 1 1 15.71 8h1.79a4.5 4.5 0 0 1 3.501 7.327",
  )

  val CloudOff = lucide(
    "cloud-off",
    "M10.94 5.274A7 7 0 0 1 15.71 10h1.79a4.5 4.5 0 0 1 4.222 6.057",
    "M18.796 18.81A4.5 4.5 0 0 1 17.5 19H9A7 7 0 0 1 5.79 5.78",
    "m2 2 20 20",
  )

  val ContactRound = lucide(
    "contact-round",
    "M16 2v2",
    "M17.915 21a6 6 0 10-12 0",
    "M8 2v2",
    "M8 11a4 4 0 1 0 8 0a4 4 0 1 0 -8 0",
    "M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2v-14a2 2 0 0 1 2 -2z",
  )

  val Download = lucide(
    "arrow-down-to-line",
    "M12 17V3",
    "m6 11 6 6 6-6",
    "M19 21H5",
  )

  val House = lucide(
    "house",
    "M15 21v-8a1 1 0 0 0-1-1h-4a1 1 0 0 0-1 1v8",
    "M3 10a2 2 0 0 1 .709-1.528l7-6a2 2 0 0 1 2.582 0l7 6A2 2 0 0 1 21 10v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z",
  )

  val IdCard = lucide(
    "id-card",
    "M16 10h2",
    "M16 14h2",
    "M6.17 15a3 3 0 0 1 5.66 0",
    "M7 11a2 2 0 1 0 4 0a2 2 0 1 0 -4 0",
    "M4 5h16a2 2 0 0 1 2 2v10a2 2 0 0 1 -2 2h-16a2 2 0 0 1 -2 -2v-10a2 2 0 0 1 2 -2z",
  )

  val Image = lucide(
    "image",
    "M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2v-14a2 2 0 0 1 2 -2z",
    "M7 9a2 2 0 1 0 4 0a2 2 0 1 0 -4 0",
    "m21 15-3.086-3.086a2 2 0 0 0-2.828 0L6 21",
  )

  val LocateFixed = lucide(
    "locate-fixed",
    "M2 12L5 12",
    "M19 12L22 12",
    "M12 2L12 5",
    "M12 19L12 22",
    "M5 12a7 7 0 1 0 14 0a7 7 0 1 0 -14 0",
    "M9 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0",
  )

  val Minus = lucide(
    "minus",
    "M5 12h14",
  )

  val Monitor = lucide(
    "monitor",
    "M4 3h16a2 2 0 0 1 2 2v10a2 2 0 0 1 -2 2h-16a2 2 0 0 1 -2 -2v-10a2 2 0 0 1 2 -2z",
    "M8 21L16 21",
    "M12 17L12 21",
  )

  val Package = lucide(
    "package",
    "M11 21.73a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73z",
    "M12 22V12",
    "M3.29 7L12 12L20.71 7",
    "m7.5 4.27 9 5.15",
  )

  val Pencil = lucide(
    "pencil",
    "M21.174 6.812a1 1 0 0 0-3.986-3.987L3.842 16.174a2 2 0 0 0-.5.83l-1.321 4.352a.5.5 0 0 0 .623.622l4.353-1.32a2 2 0 0 0 .83-.497z",
    "m15 5 4 4",
  )

  val Plus = lucide(
    "plus",
    "M5 12h14",
    "M12 5v14",
  )

  val Printer = lucide(
    "printer",
    "M6 18H4a2 2 0 0 1-2-2v-5a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v5a2 2 0 0 1-2 2h-2",
    "M6 9V3a1 1 0 0 1 1-1h10a1 1 0 0 1 1 1v6",
    "M7 14h10a1 1 0 0 1 1 1v6a1 1 0 0 1 -1 1h-10a1 1 0 0 1 -1 -1v-6a1 1 0 0 1 1 -1z",
  )

  val PrinterOff = lucide(
    "printer-check",
    "M13.5 22H7a1 1 0 0 1-1-1v-6a1 1 0 0 1 1-1h10a1 1 0 0 1 1 1v.5",
    "m16 19 2 2 4-4",
    "M6 18H4a2 2 0 0 1-2-2v-5a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v2",
    "M6 9V3a1 1 0 0 1 1-1h10a1 1 0 0 1 1 1v6",
  )

  val QrCode = lucide(
    "qr-code",
    "M4 3h3a1 1 0 0 1 1 1v3a1 1 0 0 1 -1 1h-3a1 1 0 0 1 -1 -1v-3a1 1 0 0 1 1 -1z",
    "M17 3h3a1 1 0 0 1 1 1v3a1 1 0 0 1 -1 1h-3a1 1 0 0 1 -1 -1v-3a1 1 0 0 1 1 -1z",
    "M4 16h3a1 1 0 0 1 1 1v3a1 1 0 0 1 -1 1h-3a1 1 0 0 1 -1 -1v-3a1 1 0 0 1 1 -1z",
    "M21 16h-3a2 2 0 0 0-2 2v3",
    "M21 21v.01",
    "M12 7v3a2 2 0 0 1-2 2H7",
    "M3 12h.01",
    "M12 3h.01",
    "M12 16v.01",
    "M16 12h1",
    "M21 12v.01",
    "M12 21v-1",
  )

  val ReceiptText = lucide(
    "receipt-text",
    "M13 16H8",
    "M14 8H8",
    "M16 12H8",
    "M4 3a1 1 0 0 1 1-1 1.3 1.3 0 0 1 .7.2l.933.6a1.3 1.3 0 0 0 1.4 0l.934-.6a1.3 1.3 0 0 1 1.4 0l.933.6a1.3 1.3 0 0 0 1.4 0l.933-.6a1.3 1.3 0 0 1 1.4 0l.934.6a1.3 1.3 0 0 0 1.4 0l.933-.6A1.3 1.3 0 0 1 19 2a1 1 0 0 1 1 1v18a1 1 0 0 1-1 1 1.3 1.3 0 0 1-.7-.2l-.933-.6a1.3 1.3 0 0 0-1.4 0l-.934.6a1.3 1.3 0 0 1-1.4 0l-.933-.6a1.3 1.3 0 0 0-1.4 0l-.933.6a1.3 1.3 0 0 1-1.4 0l-.934-.6a1.3 1.3 0 0 0-1.4 0l-.933.6a1.3 1.3 0 0 1-.7.2 1 1 0 0 1-1-1z",
  )

  val ScanLine = lucide(
    "scan-line",
    "M3 7V5a2 2 0 0 1 2-2h2",
    "M17 3h2a2 2 0 0 1 2 2v2",
    "M21 17v2a2 2 0 0 1-2 2h-2",
    "M7 21H5a2 2 0 0 1-2-2v-2",
    "M7 12h10",
  )

  val Search = lucide(
    "search",
    "m21 21-4.34-4.34",
    "M3 11a8 8 0 1 0 16 0a8 8 0 1 0 -16 0",
  )

  val Settings = lucide(
    "settings-2",
    "M14 17H5",
    "M19 7h-9",
    "M14 17a3 3 0 1 0 6 0a3 3 0 1 0 -6 0",
    "M4 7a3 3 0 1 0 6 0a3 3 0 1 0 -6 0",
  )

  val Share = lucide(
    "share-2",
    "M15 5a3 3 0 1 0 6 0a3 3 0 1 0 -6 0",
    "M3 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0",
    "M15 19a3 3 0 1 0 6 0a3 3 0 1 0 -6 0",
    "M8.59 13.51L15.42 17.49",
    "M15.41 6.51L8.59 10.49",
  )

  val SlidersHorizontal = lucide(
    "sliders-horizontal",
    "M10 5H3",
    "M12 19H3",
    "M14 3v4",
    "M16 17v4",
    "M21 12h-9",
    "M21 19h-5",
    "M21 5h-7",
    "M8 10v4",
    "M8 12H3",
  )

  val Store = lucide(
    "store",
    "M15 21v-5a1 1 0 0 0-1-1h-4a1 1 0 0 0-1 1v5",
    "M17.774 10.31a1.12 1.12 0 0 0-1.549 0 2.5 2.5 0 0 1-3.451 0 1.12 1.12 0 0 0-1.548 0 2.5 2.5 0 0 1-3.452 0 1.12 1.12 0 0 0-1.549 0 2.5 2.5 0 0 1-3.77-3.248l2.889-4.184A2 2 0 0 1 7 2h10a2 2 0 0 1 1.653.873l2.895 4.192a2.5 2.5 0 0 1-3.774 3.244",
    "M4 10.95V19a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-8.05",
  )

  val Tag = lucide(
    "tag",
    "M12.586 2.586A2 2 0 0 0 11.172 2H4a2 2 0 0 0-2 2v7.172a2 2 0 0 0 .586 1.414l8.704 8.704a2.426 2.426 0 0 0 3.42 0l6.58-6.58a2.426 2.426 0 0 0 0-3.42z",
    "M7 7.5a0.5 0.5 0 1 0 1 0a0.5 0.5 0 1 0 -1 0",
  )

  val Trash = lucide(
    "trash-2",
    "M10 11v6",
    "M14 11v6",
    "M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6",
    "M3 6h18",
    "M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2",
  )

  val Users = lucide(
    "users",
    "M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2",
    "M16 3.128a4 4 0 0 1 0 7.744",
    "M22 21v-2a4 4 0 0 0-3-3.87",
    "M5 7a4 4 0 1 0 8 0a4 4 0 1 0 -8 0",
  )

  val Wallet = lucide(
    "wallet",
    "M19 7V4a1 1 0 0 0-1-1H5a2 2 0 0 0 0 4h15a1 1 0 0 1 1 1v4h-3a2 2 0 0 0 0 4h3a1 1 0 0 0 1-1v-2a1 1 0 0 0-1-1",
    "M3 5v14a2 2 0 0 0 2 2h15a1 1 0 0 0 1-1v-4",
  )

  val X = lucide(
    "x",
    "M18 6 6 18",
    "m6 6 12 12",
  )
}
