/**
 * Money helpers. Amounts move through the system as integer paisa, so nothing here
 * ever rounds a float: 1 rupee is 100 paisa, quantities are thousandths of a unit.
 */

/** Parses user input like "1,250.50" into paisa. Throws on anything that is not money. */
export function parsePaisa(input: string | number) {
  const text = String(input).replace(/,/g, "").trim();
  if (!/^-?\d*(\.\d{0,2})?$/.test(text) || text === "" || text === "-") {
    throw new Error(`Invalid amount: ${input}`);
  }
  const negative = text.startsWith("-");
  const [rupees, paisa = ""] = text.replace("-", "").split(".");
  const value = Number(rupees || 0) * 100 + Number(paisa.padEnd(2, "0"));
  return negative ? -value : value;
}

/** Parses a quantity like "2.5" into thousandths. */
export function parseQuantityMilli(input: string | number) {
  const text = String(input).replace(/,/g, "").trim();
  if (!/^\d*(\.\d{0,3})?$/.test(text) || text === "") {
    throw new Error(`Invalid quantity: ${input}`);
  }
  const [whole, fraction = ""] = text.split(".");
  return Number(whole || 0) * 1000 + Number(fraction.padEnd(3, "0"));
}

/** "1,250.50" — grouped the Nepali way (lakh, crore), which is how a bill reads. */
export function formatPaisa(paisa: number) {
  const negative = paisa < 0;
  const absolute = Math.abs(paisa);
  const rupees = Math.floor(absolute / 100);
  const remainder = absolute % 100;
  const digits = String(rupees);
  const last3 = digits.slice(-3);
  const rest = digits.slice(0, -3);
  const grouped = rest ? `${rest.replace(/\B(?=(\d{2})+(?!\d))/g, ",")},${last3}` : last3;
  return `${negative ? "-" : ""}${grouped}.${String(remainder).padStart(2, "0")}`;
}

/** The plain decimal string CBMS expects, e.g. "1250.50". */
export function paisaToDecimalString(paisa: number) {
  return (paisa / 100).toFixed(2);
}

export function formatQuantity(quantityMilli: number) {
  const text = (quantityMilli / 1000).toFixed(3);
  return text.replace(/\.?0+$/, "");
}

const ONES = [
  "",
  "One",
  "Two",
  "Three",
  "Four",
  "Five",
  "Six",
  "Seven",
  "Eight",
  "Nine",
  "Ten",
  "Eleven",
  "Twelve",
  "Thirteen",
  "Fourteen",
  "Fifteen",
  "Sixteen",
  "Seventeen",
  "Eighteen",
  "Nineteen",
];
const TENS = ["", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"];

function twoDigits(value: number): string {
  if (value < 20) return ONES[value];
  const tens = TENS[Math.floor(value / 10)];
  const ones = ONES[value % 10];
  return ones ? `${tens} ${ones}` : tens;
}

function threeDigits(value: number): string {
  const hundreds = Math.floor(value / 100);
  const rest = value % 100;
  const parts = [];
  if (hundreds) parts.push(`${ONES[hundreds]} Hundred`);
  if (rest) parts.push(twoDigits(rest));
  return parts.join(" ");
}

/** Spells a rupee amount using the Nepali scale: crore, lakh, thousand, hundred. */
function rupeesToWords(rupees: number): string {
  if (rupees === 0) return "Zero";
  const parts: string[] = [];
  const scales: Array<[number, string]> = [
    [10_000_000, "Crore"],
    [100_000, "Lakh"],
    [1_000, "Thousand"],
  ];
  let remaining = rupees;
  for (const [size, name] of scales) {
    const count = Math.floor(remaining / size);
    if (count > 0) {
      // Crores above 99 keep counting in crore, the way Nepali accounting reads them.
      parts.push(
        `${size === 10_000_000 && count > 99 ? rupeesToWords(count) : threeDigits(count)} ${name}`,
      );
      remaining %= size;
    }
  }
  if (remaining > 0) parts.push(threeDigits(remaining));
  return parts.join(" ");
}

/**
 * "Rupees One Thousand Two Hundred Fifty and Fifty Paisa Only" — the amount in words
 * every tax invoice has to carry.
 */
export function amountInWords(paisa: number) {
  const negative = paisa < 0;
  const absolute = Math.abs(paisa);
  const rupees = Math.floor(absolute / 100);
  const remainder = absolute % 100;
  const head = `${negative ? "Minus " : ""}Rupees ${rupeesToWords(rupees)}`;
  return remainder > 0 ? `${head} and ${twoDigits(remainder)} Paisa Only` : `${head} Only`;
}
