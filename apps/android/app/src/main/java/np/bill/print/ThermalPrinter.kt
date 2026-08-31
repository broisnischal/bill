package np.bill.print

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Talks to the counter's thermal printer over Bluetooth serial.
 *
 * Every cheap 58/80mm printer exposes the same SPP profile, so pairing is done once in
 * Android's own Bluetooth settings and the app just picks the paired device. Printing is
 * a socket write; there is no acknowledgement to wait for, which is why a failure here
 * means "the paper may or may not have come out" and the caller offers a reprint.
 */
@Singleton
class ThermalPrinter @Inject constructor(@ApplicationContext private val context: Context) {

  data class Printer(val name: String, val address: String)

  sealed interface Outcome {
    data object Printed : Outcome
    data object NoPermission : Outcome
    data object NoPrinter : Outcome
    data class Failed(val message: String) : Outcome
  }

  private val adapter: BluetoothAdapter?
    get() = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager)?.adapter

  fun hasPermission(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) ==
      PackageManager.PERMISSION_GRANTED
  } else {
    true
  }

  /** Printers already paired in Android settings. Pairing itself is the system's job. */
  @SuppressLint("MissingPermission")
  fun paired(): List<Printer> {
    if (!hasPermission()) return emptyList()
    val bonded = runCatching { adapter?.bondedDevices }.getOrNull().orEmpty()
    return bonded
      .filter(::looksLikePrinter)
      .map { Printer(it.name ?: it.address, it.address) }
  }

  /**
   * Bluetooth device classes: 1536 is an imaging device, and the printer minor class is
   * what receipt printers report. Anything unclassified is offered too, since the cheap
   * imports often report nothing useful.
   */
  @SuppressLint("MissingPermission")
  private fun looksLikePrinter(device: BluetoothDevice): Boolean = runCatching {
    val major = device.bluetoothClass?.majorDeviceClass
    major == 1536 || major == 7936 || major == null
  }.getOrDefault(true)

  @SuppressLint("MissingPermission")
  suspend fun print(address: String, bitmap: Bitmap): Outcome = withContext(Dispatchers.IO) {
    if (!hasPermission()) return@withContext Outcome.NoPermission
    val device = runCatching { adapter?.getRemoteDevice(address) }.getOrNull()
      ?: return@withContext Outcome.NoPrinter

    var socket: BluetoothSocket? = null
    try {
      socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
      // Discovery keeps the radio busy and makes the connect time out on slower phones.
      runCatching { adapter?.cancelDiscovery() }
      socket.connect()

      val payload = EscPos.job(bitmap)
      socket.outputStream.use { stream ->
        // Cheap controllers drop bytes when a long raster arrives in one write, so the
        // job goes out in chunks with the printer given a moment between them.
        var offset = 0
        while (offset < payload.size) {
          val length = minOf(CHUNK, payload.size - offset)
          stream.write(payload, offset, length)
          stream.flush()
          offset += length
        }
      }
      Outcome.Printed
    } catch (error: IOException) {
      Outcome.Failed(error.message ?: "The printer did not answer")
    } catch (error: SecurityException) {
      Outcome.NoPermission
    } finally {
      runCatching { socket?.close() }
    }
  }

  private companion object {
    /** The serial port profile every ESC/POS printer advertises. */
    val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    const val CHUNK = 2048
  }
}
