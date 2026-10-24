# MailScout

A free Android email finder. Paste website links or search by keyword, and MailScout crawls each site's home and contact/about pages for email addresses. No license, no trial, no limits.

## Features
- Website-links mode and keyword-search mode
- Crawls contact, about, team and impressum pages; reads mailto links and obfuscated "name [at] site [dot] com" addresses
- Deduplication, syntax filtering, junk filtering (images, placeholders)
- Optional MX check (is the domain able to receive mail?) via DNS-over-HTTPS
- Respects robots.txt, 4 sites in parallel
- Filter, tap-to-copy, copy all, export CSV and share
- Share a link from your browser straight into the app
- Light and dark themes

## Get the APK (no Android Studio needed)
1. Create a free GitHub account and a new repository.
2. Upload everything in this folder to it (keep the `.github` folder).
3. Open the **Actions** tab, run **Build APK**, wait about 5 minutes.
4. Open the finished run, download **MailScout-apk**, unzip, and copy `app-release.apk` to your phone.
5. Tap it to install (allow "install unknown apps" when asked).

## Or build locally
Open the folder in Android Studio and press Run, or run `gradle assembleRelease`.

## Use responsibly
Collect only business contact addresses and follow anti-spam and data-protection laws (CAN-SPAM, GDPR, NDPA) when emailing them.
