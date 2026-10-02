# Risk-based coverage

| Risk | Test | Assertions |
| --- | --- | --- |
| Wrong debit / UI stale after transfer | `transferUpdatesUiAndApi` | visible balance, API decimal balance, recipient record |
| Invalid user receives access | `invalidCredentialsShowAccessibleError` | labelled alert, hidden account |
| Overdraft through transfer | `insufficientFundsDoesNotChangeBalance` | validation message, unchanged balance, zero transactions |
| Protected endpoint exposed | `apiRejectsAnonymousAccountAccess` | 401 + useful error |
| Duplicate request debits twice | `transferApiIsIdempotent` | 201 then 200, one transaction, changed payload rejected |
| Invalid monetary precision | `apiValidatesCurrencyPrecision` | fractional cent, zero, negative and NaN rejected |
| Outage loses money or blocks recovery | `mockedOutageRecoversWithoutDebit` | 503 UI error, no debit, successful unmocked retry |
| Auth bootstrap/reuse breaks | `authenticationStorageStateCanBeReused` | new context uses stored cookie and shows account |
| Logout only hides UI | `logoutRevokesApiSession` | sign-in returns + subsequent account API 401 |
| Download contents wrong | `statementDownloadAndReceiptUpload` | actual saved CSV row and suggested filename |
| File input flow fails | same test | synthetic receipt name rendered in local preview |
| Mobile UI unusable | `mobileLayoutSupportsTransfer` | complete transaction and no horizontal overflow |
| Keyboard flow broken | `keyboardOnlyLogin` | input/button focus sequence + successful login |
| Failure diagnostics absent | optional `intentionalBalanceMismatch` | real assertion failure with report, screenshot, trace, video |

Normal suite: 12 test methods / 13 invocations (invalid credentials has two data rows). The failure demo is a separate opt-in invocation. Each case starts with an independent synthetic account and server.

Excluded risks: real payment networks, fraud controls, production security, load testing, full accessibility conformance, native mobile apps and real provider integrations. No claim is made that this small application models an employer's banking product.
