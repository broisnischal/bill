import { execFileSync } from "node:child_process";
import { readFileSync, writeFileSync } from "node:fs";

/**
 * Turns the version changesets just wrote into the release manifest the APK is stamped
 * from and the server serves.
 *
 * Run straight after `changeset version`. Three things come out of it:
 *
 * - `versionName` follows the package version, so the changelog and the phone agree.
 * - `versionCode` goes up by one every release, because Android orders installs by that
 *   integer alone and will refuse to replace a build with a lower one.
 * - `minimumVersionCode` moves up to the new build on a major bump and stays put
 *   otherwise. That is the whole hard-or-soft decision: a major says older installs must
 *   not go on billing, anything smaller says the update can wait for a quiet counter.
 *
 * Editing `minimumVersionCode` by hand afterwards is fine and sometimes right, for the
 * release that fixes something an older build gets wrong quietly.
 */

const repo = process.env.GITHUB_REPOSITORY ?? "broisnischal/bill";

const pkg = JSON.parse(readFileSync("apps/android/package.json", "utf8"));
const previous = JSON.parse(readFileSync("apps/android/release.json", "utf8"));

if (pkg.version === previous.versionName) {
  console.log(`android: already at ${pkg.version}, nothing to sync`);
  process.exit(0);
}

const major = (value) => Number(value.split(".")[0]);
const forced = major(pkg.version) > major(previous.versionName);
const versionCode = previous.versionCode + 1;

const release = {
  versionName: pkg.version,
  versionCode,
  minimumVersionCode: forced ? versionCode : previous.minimumVersionCode,
  apkUrl: `https://github.com/${repo}/releases/download/android-v${pkg.version}/bill-${pkg.version}.apk`,
  notes: notesFor(pkg.version),
};

writeFileSync("apps/android/release.json", `${JSON.stringify(release, null, 2)}\n`);
console.log(
  `android: ${previous.versionName} (${previous.versionCode}) -> ${release.versionName} ` +
    `(${release.versionCode}), minimum ${release.minimumVersionCode}${forced ? " — forced" : ""}`,
);

/**
 * The notes the update screen shows, taken from the changelog changesets just wrote.
 *
 * Read on a phone by someone deciding whether to update mid-morning, so the markdown
 * scaffolding comes off and only the lines that say what changed are kept.
 */
function notesFor(version) {
  let changelog;
  try {
    changelog = readFileSync("apps/android/CHANGELOG.md", "utf8");
  } catch {
    return "";
  }

  const section = changelog.split(`\n## ${version}\n`)[1];
  if (!section) return "";

  return section
    .split("\n## ")[0]
    .split("\n")
    .filter((line) => line.trim() && !line.startsWith("#"))
    .map((line) => line.replace(/^[-*]\s+/, "").trim())
    .filter((line) => !/^[0-9a-f]{7,40}:/.test(line))
    .join("\n")
    .slice(0, 600)
    .trim();
}

// Keeps the tag the release workflow looks for and the manifest in one place.
export function tagFor(version) {
  return `android-v${version}`;
}

void execFileSync;
