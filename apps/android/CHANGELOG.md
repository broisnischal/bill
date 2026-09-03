# @bill/android

## 1.1.1

### Patch Changes

- The sign-in says the code comes on WhatsApp, because it does now. When the
  WhatsApp message does not go out, the screen says that rather than blaming an
  SMS that was never going to be sent.

## 1.1.0

### Minor Changes

- The credit book, for money a customer owes rather than money they paid. Pick the
  product and the customer the same way a bill does, and keep it out of the bill,
  where paid-in-full is now the only thing a bill can be.

  A bill shows the paper before it is issued. A number cannot be spent on a receipt
  somebody changes their mind about, so the confirmation comes first.

  Type an amount and the quantity follows it: Rs 200 of meat at Rs 1,300 a kilo is
  0.15 kg, and it works from either end. A unit that cannot be split floors instead
  of rounding, so Rs 480 at Rs 50 a piece is 9 pieces and not 9.6.

  Prices drop a trailing .00 and quantities read to two places for every unit.

### Patch Changes

- The tab bar hugs its tabs and centres itself, so every gap around a tab is the
  same 6dp rather than whatever was left over from stretching the row. The payment
  code sits in the middle of it and no longer looks permanently chosen.

  A list fades into the foot of its card instead of stopping in a straight line
  above the bar, which read as content somebody had cut off.

  Typing in the bill line no longer restarts the catalogue query on every frame,
  which is what the lag was.
