# Executed evidence

These are actual test artifacts, not illustrative mockups.

| Sample | Provenance | Outcome |
| --- | --- | --- |
| Passing suite | [GitHub Actions run 37050303506](https://github.com/ravitamil/playwright-java-quality-lab/actions/runs/37050303506), commit 89620ff325716392427fe3e906cab0b0ce182264, Linux + Java 17 + Playwright 1.63 matched Chromium | 13 passed; Firefox and WebKit jobs each passed 13 too |
| Failure demo | Local Windows + Java 25, Playwright Java 1.63 using cached Chromium executable override | 1 intentional failed assertion; Maven exited 1 |
| Watchable walkthrough | Same local runtime, action slow motion 200ms | 1 transfer test passed |

The local browser-download CDN timed out, so the local samples explicitly used an existing cached Chromium executable. Matching Playwright-managed browser coverage is established by the linked CI run, not inferred from that override. All application data is synthetic.

[Live evidence gallery](https://ravitamil.github.io/quality-lab/) · [Passing Extent report](passing/extent-report.html) · [Failure Extent report](failure/extent-report.html)

![Successful transfer](../images/transfer.png)

## Download and inspect

- [Transfer screenshot](passing/transferUpdatesUiAndApi-b7e1fe44/screenshot.png)
- [Transfer trace ZIP](passing/transferUpdatesUiAndApi-b7e1fe44/trace.zip)
- [Recorded walkthrough](walkthrough/transferUpdatesUiAndApi-f9e1e147/video/page@beab204ac4e4d282a60f05a135dce6b1.webm)
- [Intentional failure screenshot](failure/intentionalBalanceMismatch-3f216dcb/screenshot.png)
- [Intentional failure trace ZIP](failure/intentionalBalanceMismatch-3f216dcb/trace.zip)
- [Mobile viewport screenshot](passing/mobileLayoutSupportsTransfer-1e7c8347/screenshot.png)

GitHub displays code for HTML reports. Use the live gallery, or clone the repository and run `python -m http.server 8000 --directory docs/evidence`; open `http://localhost:8000/passing/extent-report.html`. Keep the directories together because report attachments use relative paths.

Open trace ZIPs using Playwright `show-trace` or drop them into https://trace.playwright.dev/. Full per-browser results and evidence are in the CI artifacts, retained for 14 days; this selected Chromium suite remains committed here.
