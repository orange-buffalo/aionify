# UI Review Workflow

Use this workflow whenever frontend UI is changed.

## Review Criteria

Every UI change must be reviewed with screenshots to confirm:
- no visual issues or styling glitches are present
- styling remains consistent with the existing app
- the result looks elegant on the affected screen sizes

## Screenshot Utility

`PlaywrightTestBase` provides `captureUiReviewScreenshot(...)` for manual UI review screenshots.

Generated screenshots are written to:

```text
build/ui-review-screenshots/<test-class>/<test-method>/
```

## Workflow

1. Run the most relevant functional tests for the changed area.
2. Run the most relevant Playwright test for the changed UI, with screenshot capture embedded in the functional flow.
3. Review the generated screenshots for the states affected by the change.
4. Fix any issues and repeat until the screenshots satisfy the review criteria.

Example:

```bash
TESTCONTAINERS_RYUK_DISABLED=true ./gradlew test \
  --tests "io.orangebuffalo.aionify.GoalsSettingsPlaywrightTest"
```

Use `captureUiReviewScreenshot(...)` from the relevant Playwright test instead of creating a separate screenshot-only test unless there is a strong reason to do so.
