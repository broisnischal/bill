package np.bill.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import np.bill.R
import np.bill.core.money.formatMoney
import np.bill.core.money.parsePaisa
import np.bill.ui.theme.LocalTokens
import np.bill.ui.theme.Radius

/**
 * A calculator, for the sum a shopkeeper does in their head and then mistypes.
 *
 * Counter arithmetic is nearly always the same shape: a few things added up, or a price
 * multiplied by a count. So this is four operators and a running total, not a scientific
 * calculator — and it hands the answer straight back to the amount field it was opened
 * from, which is the entire point of it existing inside the app rather than beside it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculatorSheet(
  initial: String = "",
  onResult: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  var expression by remember { mutableStateOf(initial.filter { it.isDigit() || it == '.' }) }
  val result = remember(expression) { evaluate(expression) }
  val tokens = LocalTokens.current

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
  ) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 28.dp)) {
      Column(
        Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(Radius.large))
          .background(MaterialTheme.colorScheme.surfaceContainer)
          .padding(16.dp),
        horizontalAlignment = Alignment.End,
      ) {
        Text(
          expression.ifBlank { "0" },
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.End,
        )
        Spacer(Modifier.height(4.dp))
        Text(
          result?.let { formatMoney(it) } ?: "—",
          style = MaterialTheme.typography.displaySmall,
        )
      }

      Spacer(Modifier.height(12.dp))

      val rows = listOf(
        listOf("7", "8", "9", "÷"),
        listOf("4", "5", "6", "×"),
        listOf("1", "2", "3", "−"),
        listOf(".", "0", "⌫", "+"),
      )

      for (row in rows) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          for (key in row) {
            val isOperator = key in setOf("÷", "×", "−", "+")
            Box(
              Modifier
                .weight(1f)
                .aspectRatio(1.35f)
                .clip(RoundedCornerShape(Radius.large))
                .background(
                  if (isOperator) MaterialTheme.colorScheme.surfaceContainerHigh
                  else MaterialTheme.colorScheme.surfaceContainer,
                )
                .clickable { expression = press(expression, key) },
              contentAlignment = Alignment.Center,
            ) {
              Text(
                key,
                style = MaterialTheme.typography.headlineSmall,
                color = if (isOperator) {
                  MaterialTheme.colorScheme.primary
                } else {
                  MaterialTheme.colorScheme.onSurface
                },
              )
            }
          }
        }
        Spacer(Modifier.height(8.dp))
      }

      Spacer(Modifier.height(4.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SecondaryButton(
          text = stringResource(R.string.clear_filters),
          onClick = { expression = "" },
          modifier = Modifier.weight(1f),
        )
        PrimaryButton(
          text = stringResource(R.string.use_amount),
          onClick = {
            result?.let { onResult(np.bill.core.money.paisaToInput(it)) }
            onDismiss()
          },
          enabled = result != null,
          modifier = Modifier.weight(1f),
        )
      }
    }
  }
}

private fun press(current: String, key: String): String = when (key) {
  "⌫" -> current.dropLast(1)
  "÷" -> appendOperator(current, '/')
  "×" -> appendOperator(current, '*')
  "−" -> appendOperator(current, '-')
  "+" -> appendOperator(current, '+')
  else -> current + key
}

/** One operator at a time: pressing a second replaces the first rather than stacking. */
private fun appendOperator(current: String, operator: Char): String {
  if (current.isEmpty()) return current
  return if (current.last() in "+-*/") current.dropLast(1) + operator else current + operator
}

/**
 * Left to right, in paisa.
 *
 * No operator precedence on purpose: a shopkeeper adding a column of prices reads it the
 * way they typed it, and `100+50*2` meaning 300 is what they expect from a till, not the
 * 200 that algebra would give. Returns null while the expression is not yet complete.
 */
internal fun evaluate(expression: String): Long? {
  if (expression.isBlank()) return null
  if (expression.last() in "+-*/") return null

  val tokens = mutableListOf<String>()
  val number = StringBuilder()
  for (character in expression) {
    if (character in "+-*/") {
      if (number.isEmpty()) return null
      tokens += number.toString()
      tokens += character.toString()
      number.clear()
    } else {
      number.append(character)
    }
  }
  if (number.isEmpty()) return null
  tokens += number.toString()

  var total = parsePaisa(tokens.first()) ?: return null
  var index = 1
  while (index + 1 < tokens.size) {
    val operator = tokens[index]
    val operandText = tokens[index + 1]

    total = when (operator) {
      // Multiplying and dividing money by a count: the operand is a plain number, so it
      // is parsed as rupees and then divided back down to a multiplier.
      "*" -> {
        val factor = operandText.toDoubleOrNull() ?: return null
        Math.round(total * factor)
      }
      "/" -> {
        val divisor = operandText.toDoubleOrNull() ?: return null
        if (divisor == 0.0) return null
        Math.round(total / divisor)
      }
      "+" -> total + (parsePaisa(operandText) ?: return null)
      "-" -> total - (parsePaisa(operandText) ?: return null)
      else -> return null
    }
    index += 2
  }
  return total
}
