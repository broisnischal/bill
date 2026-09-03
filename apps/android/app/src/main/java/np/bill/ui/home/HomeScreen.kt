package np.bill.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import np.bill.ui.theme.BillIcons
import np.bill.R
import np.bill.core.money.formatMoney
import np.bill.core.nepali.BsDate
import np.bill.data.db.BillEntity
import np.bill.ui.common.ActionSheet
import np.bill.ui.common.DeltaPill
import np.bill.ui.common.Hairline
import np.bill.ui.common.IconTile
import np.bill.ui.common.InitialTile
import np.bill.ui.common.MoneyDisplay
import np.bill.ui.common.Panel
import np.bill.ui.common.PrimaryButton
import np.bill.ui.common.TileTone
import np.bill.ui.theme.LocalTokens
import np.bill.ui.theme.Radius

/**
 * The first screen.
 *
 * One number, one action, and what just happened. The takings are set at display size in
 * the card the gradient sits behind, because that is the figure a shopkeeper checks
 * between customers; the paisa are dropped to grey since nobody reads them. Under it is
 * the reason the app is open at all, as the only filled control on the screen.
 *
 * Then the last few bills, each with a tinted square that says which way the money went
 * before a word has been read. Everything else a shop does is a tap away in the tabs, so
 * it is not repeated here as a grid of equal-weight tiles competing with the one action
 * that matters.
 */
@Composable
fun HomeScreen(
  miti: String,
  todayPaisa: Long,
  todayCount: Int,
  duePaisa: Long,
  pendingSync: Int,
  recent: List<BillEntity>,
  templates: List<np.bill.data.db.BillTemplate>,
  onNewBill: () -> Unit,
  onQuickBill: (String) -> Unit,
  onDeleteTemplate: (String) -> Unit,
  onAddProduct: () -> Unit,
  onAddCustomer: () -> Unit,
  onSettings: () -> Unit,
  onDues: () -> Unit,
  onBills: () -> Unit,
  onOpenBill: (String) -> Unit,
  onShowQr: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val tokens = LocalTokens.current
  var removing by androidx.compose.runtime.remember {
    androidx.compose.runtime.mutableStateOf<Pair<String, String>?>(null)
  }

  removing?.let { (id, name) ->
    // Held rather than swiped: a row of chips on a home screen is somewhere a thumb
    // rests, and a swipe-to-delete there loses a template to a mis-scroll.
    ActionSheet(
      title = stringResource(R.string.template_remove_title, name),
      subtitle = stringResource(R.string.template_remove_body),
      primary = stringResource(R.string.remove) to {
        onDeleteTemplate(id)
        removing = null
      },
      secondary = stringResource(R.string.cancel) to { removing = null },
      onDismiss = { removing = null },
    )
  }

  Column(
    modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(horizontal = 14.dp),
  ) {
    Panel {
      Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Text(
          stringResource(R.string.takings_today).uppercase(),
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          letterSpacing = 1.2.sp,
        )
        Spacer(Modifier.height(8.dp))
        MoneyDisplay(todayPaisa)
        Spacer(Modifier.height(10.dp))
        DeltaPill(
          text = androidx.compose.ui.res.pluralStringResource(
            R.plurals.bills_count,
            todayCount,
            todayCount,
          ),
          positive = todayCount > 0,
        )

        Spacer(Modifier.height(18.dp))
        PrimaryButton(
          text = stringResource(R.string.new_bill),
          onClick = onNewBill,
          icon = BillIcons.Plus,
        )

        Spacer(Modifier.height(10.dp))
        Row(
          Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.pill))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onShowQr)
            .padding(horizontal = 18.dp, vertical = 14.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.Center,
        ) {
          Icon(
            BillIcons.QrCode,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(22.dp),
          )
          Spacer(Modifier.size(8.dp))
          Text(stringResource(R.string.qr_show), style = MaterialTheme.typography.titleLarge)
        }
      }
    }

    // The shop's own baskets. First thing under the takings, because on a meat counter
    // or a kirana this is how nearly every bill starts.
    Spacer(Modifier.height(20.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        stringResource(R.string.quick_bill),
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.weight(1f),
      )
    }
    Spacer(Modifier.height(10.dp))

    if (templates.isEmpty()) {
      Text(
        stringResource(R.string.quick_bill_empty),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    } else {
      Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        for (template in templates) {
          TemplateChip(
            name = template.template.name,
            lines = template.lines.size,
            onClick = { onQuickBill(template.template.id) },
            onLongClick = { removing = template.template.id to template.template.name },
          )
        }
      }
    }

    if (duePaisa > 0) {
      Spacer(Modifier.height(12.dp))
      Panel {
        Row(
          Modifier
            .fillMaxWidth()
            .clickable(onClick = onDues)
            .padding(horizontal = 16.dp, vertical = 14.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          IconTile(BillIcons.ReceiptText, tone = TileTone.NEUTRAL)
          Spacer(Modifier.size(12.dp))
          Column(Modifier.weight(1f)) {
            Text(
              stringResource(R.string.credit_outstanding),
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
              "Rs ${formatMoney(duePaisa)}",
              style = MaterialTheme.typography.headlineMedium,
              color = tokens.due,
            )
          }
          Icon(
            BillIcons.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }

    Spacer(Modifier.height(20.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        stringResource(R.string.recent_bills),
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.weight(1f),
      )
      Text(
        stringResource(R.string.see_all),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
          .clip(RoundedCornerShape(Radius.pill))
          .clickable(onClick = onBills)
          .padding(horizontal = 8.dp, vertical = 4.dp),
      )
    }
    Spacer(Modifier.height(10.dp))

    if (recent.isEmpty()) {
      Panel {
        Text(
          stringResource(R.string.no_bills_yet),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
          modifier = Modifier.fillMaxWidth().padding(28.dp),
        )
      }
    } else {
      Panel {
        for ((index, bill) in recent.withIndex()) {
          if (index > 0) Hairline(Modifier.padding(start = 68.dp))
          ActivityRow(bill = bill, onClick = { onOpenBill(bill.id) })
        }
      }
    }

    Spacer(Modifier.height(20.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      QuickAction(
        icon = BillIcons.Package,
        label = stringResource(R.string.add_product),
        onClick = onAddProduct,
        modifier = Modifier.weight(1f),
      )
      QuickAction(
        icon = BillIcons.Users,
        label = stringResource(R.string.add_customer),
        onClick = onAddCustomer,
        modifier = Modifier.weight(1f),
      )
    }

    Spacer(Modifier.height(28.dp))
  }
}

/** One bill, the way the shop reads it: who, which number, how much. */
@Composable
private fun ActivityRow(bill: BillEntity, onClick: () -> Unit) {
  val cancelled = bill.status == "cancelled"

  Row(
    Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(horizontal = 16.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    IconTile(
      icon = BillIcons.ReceiptText,
      tone = if (cancelled) TileTone.NEGATIVE else TileTone.MINT,
    )
    Spacer(Modifier.size(12.dp))
    Column(Modifier.weight(1f)) {
      Text(
        bill.buyerName,
        style = MaterialTheme.typography.bodyLarge,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textDecoration = if (cancelled) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
      )
      Text(
        BsDate.parse(bill.miti)?.formatLong() ?: bill.miti,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
    Spacer(Modifier.size(8.dp))
    Text(
      "Rs ${formatMoney(bill.totalPaisa)}",
      style = MaterialTheme.typography.titleLarge,
      color = if (cancelled) {
        MaterialTheme.colorScheme.onSurfaceVariant
      } else {
        MaterialTheme.colorScheme.onSurface
      },
      textDecoration = if (cancelled) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
    )
  }
}

/**
 * One saved basket. The name, and how many lines come with it.
 *
 * Tap starts the bill; hold offers to remove it. Nothing about it is destructive on a
 * single tap, because it sits where a thumb scrolls.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun TemplateChip(
  name: String,
  lines: Int,
  onClick: () -> Unit,
  onLongClick: () -> Unit,
) {
  val tokens = LocalTokens.current
  val shape = RoundedCornerShape(Radius.pill)

  Row(
    Modifier
      .clip(shape)
      .background(MaterialTheme.colorScheme.surface)
      .border(1.dp, tokens.borderStrong, shape)
      .combinedClickable(onClick = onClick, onLongClick = onLongClick)
      .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    // A rounded square inside a stadium never sits right. Circle in a pill.
    InitialTile(name, size = 34.dp)
    Spacer(Modifier.size(10.dp))
    Column {
      Text(name, style = MaterialTheme.typography.titleLarge, maxLines = 1)
      Text(
        androidx.compose.ui.res.pluralStringResource(R.plurals.line_count, lines, lines),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

/** A secondary job, as a card wide enough to hit without looking. */
@Composable
private fun QuickAction(
  icon: ImageVector,
  label: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Panel(modifier) {
    Column(
      Modifier
        .fillMaxWidth()
        .clickable(onClick = onClick)
        .padding(vertical = 18.dp, horizontal = 14.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      IconTile(icon, tone = TileTone.MINT, size = 54.dp)
      Spacer(Modifier.height(10.dp))
      Text(
        label,
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center,
        maxLines = 2,
      )
    }
  }
}
