# Future Android Improvements

This document captures the Android platform work that would improve compatibility, maintainability, and long-term support in future releases of GBCamera Android Manager.

## 1. Move off legacy shared-storage access

The app still uses legacy public external storage paths and legacy storage-related flags in the codebase. The current implementation works, but it is the main source of platform risk as Android continues to tighten storage access.

Planned work:
- Replace `Environment.getExternalStoragePublicDirectory(...)` and `Environment.getExternalStorageDirectory()` usage with app-scoped storage and modern `MediaStore` or Storage Access Framework flows.
- Remove `android:requestLegacyExternalStorage="true"` once storage access is migrated.
- Reduce or eliminate the need for `MANAGE_EXTERNAL_STORAGE` where possible.

Expected benefits:
- Better compatibility with newer Android versions.
- Fewer permission prompts and fewer policy concerns.
- Cleaner handling of shared files, exports, and backups.

## 2. Finish migrating file picking to Activity Result APIs

There is still at least one legacy file-picker path that uses `startActivityForResult(...)`, even though the app already uses `registerForActivityResult(...)` in other places.

Planned work:
- Replace remaining `startActivityForResult(...)` usage with Activity Result Contracts.
- Prefer `OpenDocument`, `GetContent`, or `PickVisualMedia` depending on the source type.
- Standardize file import flows so the app handles permissions and returned URIs consistently.

Expected benefits:
- More reliable behavior on modern Android.
- Less fragment/activity lifecycle complexity.
- Easier maintenance of import and share flows.

## 3. Replace AsyncTask-based background work

The app still uses many `AsyncTask` classes across gallery, import, USB serial, and save flows. `AsyncTask` is deprecated and makes lifecycle handling harder than it needs to be.

Planned work:
- Replace `AsyncTask` with `ExecutorService`, coroutines, or `WorkManager` depending on the job type.
- Use lifecycle-aware state updates for UI-bound operations.
- Keep long-running USB or file operations cancelable and resilient to configuration changes.

Expected benefits:
- Better stability during rotation and process recreation.
- Clearer threading model.
- Easier debugging and future refactoring.

## 4. Modernize notifications for Android 13+

Notification support is already present, but it can be improved for newer Android versions and for any future background work.

Planned work:
- Gate notification posting on runtime permission state for API 33+.
- Review foreground progress flows for long-running import/export/USB operations.
- Add richer progress and completion messaging where it improves user feedback.

Expected benefits:
- Better user visibility for important operations.
- Fewer silent failures on newer Android versions.
- A cleaner path for long-running tasks.

## 5. Raise the Java/toolchain baseline when practical

The app currently compiles with Java 8 compatibility. That still works, but the Android toolchain increasingly centers on newer Java versions.

Planned work:
- Evaluate moving the module toolchain closer to Java 17.
- Confirm third-party library compatibility before changing the baseline.
- Adjust Gradle and source compatibility settings in a controlled update.

Expected benefits:
- Better alignment with current Android Studio and AGP expectations.
- Easier adoption of newer APIs and libraries.
- Fewer build warnings over time.

## Suggested order

If this roadmap is implemented incrementally, the best order is:
1. Finish storage migration.
2. Replace the remaining legacy file-picker path.
3. Remove `AsyncTask` from the most important background jobs.
4. Expand notification and foreground-task handling.
5. Raise the Java/toolchain baseline after library compatibility is checked.

## Notes

The app already builds successfully with `compileSdk 35`, `targetSdk 35`, and `minSdk 23`. This document is intended to track the next round of platform modernization work, not the SDK bump already completed.