import { mkdir, rm, cp, rename } from "fs/promises";
import { existsSync } from "fs";
import { createHash } from "crypto";
import { basename, join } from "path";

const outDir = join(import.meta.dir, "dist");
const publicDir = join(import.meta.dir, "public");

// Clean output directory
if (existsSync(outDir)) {
  await rm(outDir, { recursive: true });
}
await mkdir(outDir, { recursive: true });

// Copy public directory if it exists
if (existsSync(publicDir)) {
  await cp(publicDir, outDir, { recursive: true });
}

// Build React application with Bun
const buildResult = await Bun.build({
  entrypoints: [join(import.meta.dir, "src/main.tsx")],
  outdir: outDir,
  minify: true,
  splitting: false,
  target: "browser",
  format: "esm",
  sourcemap: true,
  naming: {
    entry: "[name]-[hash].[ext]",
    chunk: "[name]-[hash].[ext]",
    asset: "[name]-[hash].[ext]",
  },
});

if (!buildResult.success) {
  console.error("Build failed:");
  for (const log of buildResult.logs) {
    console.error(log);
  }
  process.exit(1);
}

const scriptOutput = buildResult.outputs.find((output) => output.path.endsWith(".js"));
if (!scriptOutput) {
  console.error("Build failed: JavaScript bundle was not generated");
  process.exit(1);
}
const scriptFileName = basename(scriptOutput.path);

const cssInput = join(import.meta.dir, "src/styles.css");
const cssOutput = join(outDir, "styles.css");

const cssProcess = Bun.spawn(["bunx", "@tailwindcss/cli", "-i", cssInput, "-o", cssOutput, "--minify"], {
  cwd: import.meta.dir,
  stdout: "inherit",
  stderr: "inherit",
});

await cssProcess.exited;

if (cssProcess.exitCode !== 0) {
  console.error("Build failed: Tailwind CSS compilation failed");
  process.exit(1);
}

const cssContents = await Bun.file(cssOutput).arrayBuffer();
const cssHash = createHash("sha256").update(new Uint8Array(cssContents)).digest("hex").slice(0, 16);
const stylesFileName = `styles-${cssHash}.css`;
await rename(cssOutput, join(outDir, stylesFileName));

// Create HTML file
const html = `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Aionify - Time Tracking</title>
  <link rel="icon" type="image/svg+xml" href="/favicon.svg">
  <link rel="stylesheet" href="/${stylesFileName}">
</head>
<body>
  <div id="root"></div>
  <script type="module" src="/${scriptFileName}"></script>
</body>
</html>`;

await Bun.write(join(outDir, "index.html"), html);

console.log("Build complete! Output in dist/");
console.log("Generated files:");
for (const output of buildResult.outputs) {
  console.log(`  - ${output.path.split("/").pop()}`);
}
console.log("  - index.html");
console.log(`  - ${stylesFileName}`);
