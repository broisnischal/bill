package np.bill.ui.karobar

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import np.bill.R
import np.bill.core.money.formatMoney
import np.bill.core.nepali.BsDate
import np.bill.data.db.KarobarEntryEntity
import np.bill.ui.common.ActionSheet
import np.bill.ui.common.BottomAction
import np.bill.ui.common.EmptyState
import np.bill.ui.common.Field
import np.bill.ui.common.FormSheet
import np.bill.ui.common.Hairline
import np.bill.ui.common.InitialTile
import np.bill.ui.common.MoneyDisplay
import np.bill.ui.common.Panel
import np.bill.ui.common.PrimaryButton
import np.bill.ui.theme.Gutter
import np.bill.ui.theme.LocalTokens

/**
 * The credit book.
 *
 * What a customer took and has not paid for. Every shop keeps one of these in a notebook
 * by the till, and it is not a bill: a bill takes a number out of a government series and
 * is immutable the moment it is issued, while this is a note that gets corrected. When
 * the money arrives the shop marks it paid and makes a bill for it.
 */
@Composable
fun KarobarScreen(
  modifier: Modifier = Modifier,
  viewModel: KarobarViewModel = hiltViewModel(),
) {
  val open by viewModel.open.collectAsStateWithLifecycle()
  val settled by viewModel.settled.collectAsStateWithLifecycle()
  val outstanding by viewModel.outstanding.collectAsStateWithLifecycle()
  val tokens = LocalTokens.current

  var adding by remember { mutableStateOf(false) }
  var acting by remember { mutableStateOf<KarobarEntryEntity?>(null) }

  if (adding) {
    AddEntrySheet(
      viewModel = viewModel,
      onDismiss = {
        viewModel.reset()
        adding = false
      },
    )
  }

  acting?.let { entry ->
    ActionSheet(
      title = entry.buyerName,
      subtitle = stringResource(
        if (entry.settledAt == null) R.string.karobar_owes else R.string.karobar_paid_on,
        formatMoney(entry.amountPaisa),
      ),
      primary = stringResource(
        if (entry.settledAt == null) R.string.karobar_mark_paid else R.string.karobar_reopen,
      ) to {
        if (entry.settledAt == null) viewModel.settle(entry) else viewModel.reopen(entry)
        acting = null
      },
      secondary = stringResource(R.string.remove) to {
        viewModel.delete(entry)
        acting = null
      },
      onDismiss = { acting = null },
    )
  }

  Column(modifier.fillMaxSize()) {
    Column(
      Modifier
        .weight(1f)
        .verticalScroll(rememberScrollState())
        .padding(horizontal = Gutter),
    ) {
      Panel {
        Column(
          Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 18.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Text(
            stringResource(R.string.karobar_outstanding).uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Spacer(Modifier.height(6.dp))
          MoneyDisplay(outstanding, style = MaterialTheme.typography.displayMedium)
          Spacer(Modifier.height(4.dp))
          Text(
            stringResource(R.string.karobar_people, open.size),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      Spacer(Modifier.height(20.dp))
      if (open.isEmpty()) {
        Panel {
          Text(
            stringResource(R.string.karobar_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(28.dp),
          )
        }
      } else {
        Panel {
          for ((index, entry) in open.withIndex()) {
            if (index > 0) Hairline(Modifier.padding(start = 68.dp))
            EntryRow(entry = entry, onClick = { acting = entry })
          }
        }
      }

      if (settled.isNotEmpty()) {
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.karobar_settled), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(10.dp))
        Panel {
          for ((index, entry) in settled.withIndex()) {
            if (index > 0) Hairline(Modifier.padding(start = 68.dp))
            EntryRow(entry = entry, onClick = { acting = entry })
          }
        }
      }

      Spacer(Modifier.height(24.dp))
    }

    BottomAction(text = stringResource(R.string.karobar_add), onClick = { adding = true })
  }
}

/** One line of the book: who, what, how much, and whether it is still out. */
@Composable
private fun EntryRow(entry: KarobarEntryEntity, onClick: () -> Unit) {
  val paid = entry.settledAt != null
  val tokens = LocalTokens.current

  Row(
    Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(horizontal = 16.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    InitialTile(entry.buyerName)
    Spacer(Modifier.size(12.dp))
    Column(Modifier.weight(1f)) {
      Text(
        entry.buyerName,
        style = MaterialTheme.typography.bodyLarge,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textDecoration = if (paid) TextDecoration.LineThrough else null,
      )
      Text(
        "${entry.description} · ${BsDate.parse(entry.miti)?.formatLong() ?: entry.miti}",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
    Spacer(Modifier.size(8.dp))
    Text(
      "Rs ${formatMoney(entry.amountPaisa)}",
      style = MaterialTheme.typography.titleLarge,
      color = if (paid) MaterialTheme.colorScheme.onSurfaceVariant else tokens.due,
      textDecoration = if (paid) TextDecoration.LineThrough else null,
    )
  }
}

/** Four things: who took it, what it was, how much, and anything worth remembering. */
@Composable
private fun AddEntrySheet(viewModel: KarobarViewModel, onDismiss: () -> Unit) {
  val form by viewModel.form.collectAsStateWithLifecycle()

  FormSheet(
    title = stringResource(R.string.karobar_add),
    subtitle = stringResource(R.string.karobar_add_detail),
    onDismiss = onDismiss,
    heightFraction = 0.82f,
    action = {
      PrimaryButton(
        text = stringResource(R.string.save),
        onClick = { viewModel.save(onDismiss) },
        enabled = form.valid,
      )
    },
  ) {
    Field(
      value = form.buyerName,
      onValueChange = { value -> viewModel.onForm { it.copy(buyerName = value) } },
      label = stringResource(R.string.buyer_name),
    )
    Field(
      value = form.description,
      onValueChange = { value -> viewModel.onForm { it.copy(description = value) } },
      label = stringResource(R.string.karobar_what),
      hint = stringResource(R.string.karobar_what_hint),
    )
    Field(
      value = form.amount,
      onValueChange = { value -> viewModel.onForm { it.copy(amount = value) } },
      label = stringResource(R.string.amount),
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
    Field(
      value = form.buyerPhone,
      onValueChange = { value -> viewModel.onForm { it.copy(buyerPhone = value) } },
      label = stringResource(R.string.buyer_phone),
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
    )
    Field(
      value = form.note,
      onValueChange = { value -> viewModel.onForm { it.copy(note = value) } },
      label = stringResource(R.string.notes),
      hint = stringResource(R.string.karobar_note_hint),
    )
    Spacer(Modifier.height(8.dp))
  }
}
