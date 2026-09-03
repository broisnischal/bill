package np.bill.ui.credit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.clip
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
import np.bill.data.db.CreditEntryEntity
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
import np.bill.ui.theme.Radius
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
fun CreditBookScreen(
  modifier: Modifier = Modifier,
  viewModel: CreditBookViewModel = hiltViewModel(),
) {
  val open by viewModel.open.collectAsStateWithLifecycle()
  val settled by viewModel.settled.collectAsStateWithLifecycle()
  val outstanding by viewModel.outstanding.collectAsStateWithLifecycle()
  val tokens = LocalTokens.current

  var adding by remember { mutableStateOf(false) }
  var acting by remember { mutableStateOf<CreditEntryEntity?>(null) }

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
        if (entry.settledAt == null) R.string.credit_owes else R.string.credit_paid_on,
        formatMoney(entry.amountPaisa),
      ),
      primary = stringResource(
        if (entry.settledAt == null) R.string.credit_mark_paid else R.string.credit_reopen,
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
            stringResource(R.string.credit_outstanding).uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Spacer(Modifier.height(6.dp))
          MoneyDisplay(outstanding, style = MaterialTheme.typography.displayMedium)
          Spacer(Modifier.height(4.dp))
          Text(
            stringResource(R.string.credit_people, open.size),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      Spacer(Modifier.height(20.dp))
      if (open.isEmpty()) {
        Panel {
          Text(
            stringResource(R.string.credit_empty),
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
        Text(stringResource(R.string.credit_settled), style = MaterialTheme.typography.headlineSmall)
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

    BottomAction(text = stringResource(R.string.credit_add), onClick = { adding = true })
  }
}

/** One line of the book: who, what, how much, and whether it is still out. */
@Composable
private fun EntryRow(entry: CreditEntryEntity, onClick: () -> Unit) {
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
private fun AddEntrySheet(viewModel: CreditBookViewModel, onDismiss: () -> Unit) {
  val form by viewModel.form.collectAsStateWithLifecycle()
  val regulars by viewModel.recentBuyers.collectAsStateWithLifecycle()
  val products by viewModel.products.collectAsStateWithLifecycle()
  val customers by viewModel.customers.collectAsStateWithLifecycle()
  val tokens = LocalTokens.current
  var pickingProduct by remember { mutableStateOf(false) }
  var pickingCustomer by remember { mutableStateOf(false) }

  // The two lists, in the same sheet every picker in the app uses.
  if (pickingProduct) {
    np.bill.ui.common.ChoiceSheet(
      title = stringResource(R.string.pick_product),
      options = products.map { it.name },
      selected = form.description,
      searchable = true,
      onPick = {
        viewModel.useItemNamed(it)
        pickingProduct = false
      },
      onDismiss = { pickingProduct = false },
    )
  }

  if (pickingCustomer) {
    np.bill.ui.common.ChoiceSheet(
      title = stringResource(R.string.search_customers),
      options = customers.map { it.name },
      selected = form.buyerName,
      searchable = true,
      onPick = {
        viewModel.useCustomerNamed(it)
        pickingCustomer = false
      },
      onDismiss = { pickingCustomer = false },
    )
  }

  FormSheet(
    title = stringResource(R.string.credit_add),
    subtitle = stringResource(R.string.credit_add_detail),
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
    // The regulars this counter has served, same as on a bill. One tap fills the name
    // and the phone from the last time.
    if (regulars.isNotEmpty() && form.buyerName.isBlank()) {
      Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        for (buyer in regulars) {
          Row(
            Modifier
              .clip(RoundedCornerShape(Radius.pill))
              .background(MaterialTheme.colorScheme.surface)
              .border(1.dp, tokens.borderStrong, RoundedCornerShape(Radius.pill))
              .clickable { viewModel.useBuyer(buyer) }
              .padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            InitialTile(buyer.name, size = 30.dp)
            Spacer(Modifier.size(8.dp))
            Text(buyer.name, style = MaterialTheme.typography.labelLarge, maxLines = 1)
          }
        }
      }
      Spacer(Modifier.height(10.dp))
    }

    Field(
      value = form.buyerName,
      onValueChange = { value -> viewModel.onForm { it.copy(buyerName = value) } },
      label = stringResource(R.string.buyer_name),
      trailingIcon = {
        androidx.compose.material3.IconButton(onClick = { pickingCustomer = true }) {
          androidx.compose.material3.Icon(
            np.bill.ui.theme.BillIcons.Users,
            contentDescription = stringResource(R.string.search_customers),
            tint = MaterialTheme.colorScheme.primary,
          )
        }
      },
    )

    Field(
      value = form.description,
      onValueChange = { value -> viewModel.onForm { it.copy(description = value) } },
      label = stringResource(R.string.credit_what),
      hint = stringResource(R.string.credit_what_hint),
      trailingIcon = {
        androidx.compose.material3.IconButton(onClick = { pickingProduct = true }) {
          androidx.compose.material3.Icon(
            np.bill.ui.theme.BillIcons.Package,
            contentDescription = stringResource(R.string.pick_product),
            tint = MaterialTheme.colorScheme.primary,
          )
        }
      },
    )

    // What the shop already sells, matched against what is being typed. Picking one
    // fills the price too, which is the whole reason to offer them.
    val matches by remember(form.description) {
      if (form.description.trim().length >= 2) {
        viewModel.itemSuggestions(form.description)
      } else {
        kotlinx.coroutines.flow.flowOf(emptyList())
      }
    }.collectAsStateWithLifecycle(emptyList())

    if (matches.isNotEmpty() && matches.none { it.name.equals(form.description.trim(), true) }) {
      Panel {
        for ((index, match) in matches.take(4).withIndex()) {
          if (index > 0) Hairline()
          Row(
            Modifier
              .fillMaxWidth()
              .clickable { viewModel.useItem(match) }
              .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(match.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(
              "Rs ${formatMoney(match.unitPricePaisa)}",
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
      Spacer(Modifier.height(10.dp))
    }
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
      hint = stringResource(R.string.credit_note_hint),
    )
    Spacer(Modifier.height(8.dp))
  }
}
