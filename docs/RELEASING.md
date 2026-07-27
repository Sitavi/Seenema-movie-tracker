# Releasing

F-Droid builds from a git tag and signs with its own key; it never downloads the
APK from a GitHub release. Its metadata lives in
[fdroiddata](https://gitlab.com/fdroid/fdroiddata) as `metadata/sitavi.seenema.yml`
and is maintained there, not here.

For each new version:

1. Bump `versionCode` (always up by one) and `versionName` in `app/build.gradle.kts`.
2. Add `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`.
3. Commit, then tag and push it:

       git tag -a v1.2 -m "Seenema 1.2"
       git push origin v1.2

F-Droid's bot notices new tags on its own and adds the build entry.
