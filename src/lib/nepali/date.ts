import NepaliDate from "nepali-date-converter";

/** Bikram Sambat month names, index 0 is Baisakh. */
export const BS_MONTHS = [
  "Baisakh",
  "Jestha",
  "Ashad",
  "Shrawan",
  "Bhadra",
  "Ashwin",
  "Kartik",
  "Mangsir",
  "Poush",
  "Magh",
  "Falgun",
  "Chaitra",
] as const;

export const BS_MONTHS_NEPALI = [
  "बैशाख",
  "जेठ",
  "असार",
  "श्रावण",
  "भाद्र",
  "आश्विन",
  "कार्तिक",
  "मंसिर",
  "पौष",
  "माघ",
  "फाल्गुन",
  "चैत्र",
] as const;

const NEPALI_DIGITS = ["०", "१", "२", "३", "४", "५", "६", "७", "८", "९"];

/** Nepal Standard Time is UTC+05:45 and has no daylight saving. */
const NPT_OFFSET_MINUTES = 5 * 60 + 45;

const pad = (value: number) => String(value).padStart(2, "0");

/** Rewrites ASCII digits as Devanagari, for the Nepali-language side of a bill. */
export function toNepaliDigits(value: string) {
  return value.replace(/\d/g, (digit) => NEPALI_DIGITS[Number(digit)]);
}

/** The wall-clock date in Kathmandu for an instant, which is what a bill date means. */
export function nptParts(at: Date) {
  const shifted = new Date(at.getTime() + NPT_OFFSET_MINUTES * 60_000);
  return {
    year: shifted.getUTCFullYear(),
    month: shifted.getUTCMonth() + 1,
    day: shifted.getUTCDate(),
    hour: shifted.getUTCHours(),
    minute: shifted.getUTCMinutes(),
    second: shifted.getUTCSeconds(),
  };
}

/** `YYYY-MM-DD` in the Gregorian calendar, Kathmandu time. */
export function toAdDateString(at: Date) {
  const { year, month, day } = nptParts(at);
  return `${year}-${pad(month)}-${pad(day)}`;
}

/** `HH:MM:SS` in Kathmandu time. */
export function toNptTimeString(at: Date) {
  const { hour, minute, second } = nptParts(at);
  return `${pad(hour)}:${pad(minute)}:${pad(second)}`;
}

export interface BsDate {
  year: number;
  /** 1-12, Baisakh is 1. */
  month: number;
  day: number;
}

/** Converts an instant to the Bikram Sambat date it falls on in Kathmandu. */
export function toBs(at: Date): BsDate {
  const { year, month, day } = nptParts(at);
  const bs = new NepaliDate(new Date(Date.UTC(year, month - 1, day, 12))).getBS();
  return { year: bs.year, month: bs.month + 1, day: bs.date };
}

/** `YYYY-MM-DD` in Bikram Sambat, the format stored in `invoice.miti`. */
export function toBsString(at: Date) {
  const bs = toBs(at);
  return `${bs.year}-${pad(bs.month)}-${pad(bs.day)}`;
}

/** Parses `YYYY-MM-DD` Bikram Sambat back to the Gregorian date it starts on. */
export function bsStringToAd(value: string) {
  const [year, month, day] = value.split("-").map(Number);
  if (!year || !month || !day) throw new Error(`Invalid BS date: ${value}`);
  return new NepaliDate(year, month - 1, day).toJsDate();
}

export function formatBsLong(value: string, nepali = false) {
  const [year, month, day] = value.split("-").map(Number);
  const name = nepali ? BS_MONTHS_NEPALI[month - 1] : BS_MONTHS[month - 1];
  const text = `${day} ${name} ${year}`;
  return nepali ? toNepaliDigits(text) : text;
}

/**
 * The Nepali fiscal year an instant falls in, in the notation the IRD uses on returns
 * and in CBMS payloads: "2082.083". The year runs Shrawan 1 to the end of Ashad, so
 * BS months 4 through 12 open the year and months 1 through 3 close the previous one.
 */
export function fiscalYearFor(at: Date) {
  const { year, month } = toBs(at);
  const startYear = month >= 4 ? year : year - 1;
  return `${startYear}.${String(startYear + 1).slice(1)}`;
}

/** First and last instant of a fiscal year, as UTC timestamps, for range queries. */
export function fiscalYearRange(fiscalYear: string) {
  const startYear = Number(fiscalYear.split(".")[0]);
  if (!Number.isInteger(startYear)) throw new Error(`Invalid fiscal year: ${fiscalYear}`);
  const start = new NepaliDate(startYear, 3, 1).toJsDate();
  const end = new NepaliDate(startYear + 1, 3, 1).toJsDate();
  return { start, end };
}

/** The fiscal years a store could reasonably be filing, newest first. */
export function recentFiscalYears(at = new Date(), count = 5) {
  const current = Number(fiscalYearFor(at).split(".")[0]);
  return Array.from({ length: count }, (_, index) => {
    const year = current - index;
    return `${year}.${String(year + 1).slice(1)}`;
  });
}
