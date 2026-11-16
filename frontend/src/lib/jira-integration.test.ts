import { describe, expect, test } from "bun:test";
import { readFileSync } from "node:fs";
import { runInNewContext } from "node:vm";

const script = readFileSync(new URL("../../../supplements/integrations/aionify-jira.user.js", import.meta.url), "utf8");
const newHeading =
  '[data-testid="issue-field-single-line-text-inline-edit-heading.ui.single-line-text-heading.read-view"]';
const oldHeadings = [
  '[data-testid="issue.views.issue-base.foundation.summary.heading"]',
  "#summary-val",
  '[data-test-id="issue.views.field.rich-text.heading"]',
];

function loadIntegration(titles: Record<string, string>) {
  const queries: string[] = [];
  let config!: { getTitleForCurrentPage: () => Promise<string> };
  runInNewContext(script, {
    console: { debug() {} },
    URLSearchParams,
    document: {
      querySelector(selector: string) {
        queries.push(selector);
        return selector in titles ? { textContent: titles[selector] } : null;
      },
    },
    window: {
      location: { pathname: "/browse/PROJ-123", search: "" },
      Aionify: {
        Client: class {},
        Menu: class {
          constructor(_client: unknown, menuConfig: typeof config) {
            config = menuConfig;
          }
          initialize() {}
        },
      },
    },
  });
  return { config, queries };
}

describe("Jira integration title detection", () => {
  test("reads the new inline-edit heading before legacy headings", async () => {
    const { config, queries } = loadIntegration({
      [newHeading]: "  New title  ",
      [oldHeadings[0]]: "Old title",
    });
    expect(await config.getTitleForCurrentPage()).toBe("PROJ-123 New title");
    expect(queries).toEqual([newHeading]);
  });

  for (const selector of oldHeadings) {
    test(`falls back to ${selector}`, async () => {
      const { config, queries } = loadIntegration({ [selector]: "  Legacy title  " });
      expect(await config.getTitleForCurrentPage()).toBe("PROJ-123 Legacy title");
      expect(queries[0]).toBe(newHeading);
    });
  }

  test("skips empty new and legacy headings", async () => {
    const { config } = loadIntegration({
      [newHeading]: " \n ",
      [oldHeadings[0]]: "",
      [oldHeadings[1]]: "Server title",
    });
    expect(await config.getTitleForCurrentPage()).toBe("PROJ-123 Server title");
  });

  test("keeps legacy selector priority independent of DOM order", async () => {
    const { config } = loadIntegration({
      [oldHeadings[2]]: "Rich text title",
      [oldHeadings[1]]: "Server title",
      [oldHeadings[0]]: "Cloud title",
    });
    expect(await config.getTitleForCurrentPage()).toBe("PROJ-123 Cloud title");
  });

  test("uses only the issue key when no heading has text", async () => {
    for (const titles of [{}, { [newHeading]: " ", [oldHeadings[0]]: "" }]) {
      const { config } = loadIntegration(titles);
      expect(await config.getTitleForCurrentPage()).toBe("PROJ-123");
    }
  });
});
