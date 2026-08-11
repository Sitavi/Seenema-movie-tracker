<p align="center">
  <img src="fastlane/metadata/android/en-US/images/icon.png" alt="Seenema" width="112">
</p>

<h1 align="center">Seenema: Movie Tracker</h1>

<p align="center">
  Track the films and series you have watched, rate them, and keep a list of what to watch next.<br>
  No account, no ads, no tracking, locally saved.
</p>

<p align="center">
  <a href="https://f-droid.org/packages/sitavi.seenema/">
    <img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png" alt="Get it on F-Droid" height="80">
  </a>
</p>

<p align="center">
  <img src="docs/screenshots/hero.png" alt="Seenema: the seen list, search results, a film page, and a director's filmography" width="100%">
</p>

## Features

- 🔍 **Search** films, series and people.
- ⭐ **Rate in one tap** a film or a series to add it to your seen list.
- 🕐 **Watch later** saves anything for later, and it moves over to your seen list once you rate it.
- 🎞️ **Title pages** with the synopsis, IMDb score, cast, complete crew, and trailer, which plays in the app.
- 👤 **People pages** with their full filmography.
- 📝 **Notes** field to add a personal note and the date you watched something.
- 📄 **Backup** is easy with a plain CSV file on your phone, not an account somewhere.

Everything sits in `Android/media/sitavi.seenema/seenema.csv`. "Share CSV" in the top-right menu exports it and "Import CSV" brings it back, which is how you move to a new phone or restore a backup.

> ⚠️ Android deletes that folder when the app is uninstalled, so regular backups are recommended.
