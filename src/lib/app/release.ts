import manifest from "../../../apps/android/release.json";

/**
 * What the newest Android build is, and how old an install may be.
 *
 * The numbers come from `apps/android/release.json`, which Gradle also stamps the APK
 * from, so the version a phone reports and the version it is compared against are one
 * file rather than two places to keep in step.
 *
 * `minimumVersionCode` is the whole hard-or-soft decision. An install below it is held
 * at the update screen; anything above it is offered the update and can carry on
 * selling. `scripts/sync-android-version.mjs` raises it on a major bump and leaves it
 * where it is otherwise, so forcing an update is something a release says out loud.
 */
export interface AppRelease {
  versionName: string;
  versionCode: number;
  minimumVersionCode: number;
  apkUrl: string;
  notes: string;
}

export const androidRelease: AppRelease = manifest;
