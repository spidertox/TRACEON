# Known limitations (stated up front, not hidden)

1. **Nothing here was compiled or run where it was written.** See README "verification status".
2. **No automatic item-level tracking of other apps.** Android does not expose what video/reel/page is on screen in YouTube, Instagram, Facebook, Snapchat, Moj or Josh.
   UsageStats gives app-level time only. Item-level history comes from Takeout/imports, Share, manual entry or the TRACEON browser.
3. **Accessibility integration is not implemented.** It would be brittle (view ids change per app version), is policy-sensitive, and could not be tested here. The Permissions screen says so.
4. **Other browsers** are not observable. Use exported history (Takeout `BrowserHistory.json` or CSV). Firefox/Brave/Edge/Opera/Samsung have no common export; their CSV/JSON exports from extensions work if they contain url and time.
5. **Day/month ambiguous dates** (01/02/2024) are rejected rather than guessed.
6. **Import size:** files over 60 MB are refused (whole-file read); restore files over 200 MB likewise. Export a shorter range instead.
7. **Classification** is a transparent keyword heuristic, English-centric, with 26 built-in categories. Its confidence is a ranking signal, not a probability. Content type from URLs is a guess unless the platform URL is recognised (then marked accordingly).
8. **Cloud AI** is not part of this build (so there is nothing to opt into). Smart Discovery is rule-based and offline.
9. **Time on page** is measured only in the TRACEON browser and only while the screen is in the foreground.
10. **Daily buckets** use the device time zone offset at query time; around a DST change an event within one hour of midnight can land in the adjacent day.
11. **Graph:** loads at most the 5000 most recent matching items; radial layout, so very large topics rely on paging.
12. **Weekly chart** shows rolling 7-day windows, not calendar weeks.
13. **Database is not encrypted at rest.**
14. **Tablets/foldables/Android versions** were not exercised; minSdk is 26.
15. Package names of Moj and Josh in the catalog are best known values and may differ by region.
16. **Cleartext `http://` sites do not load** in the TRACEON browser (Android blocks cleartext traffic for this app); bare domains are opened over `https://`.
17. **Release APKs are not minified by default** (R8 off) until the app has been exercised on a device.
