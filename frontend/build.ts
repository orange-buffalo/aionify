import { mkdir, rm } from "fs/promises"
import { existsSync } from "fs"
import { join } from "path"

const outDir = join(import.meta.dir, "dist")

// Clean output directory
if (existsSync(outDir)) {
  await rm(outDir, { recursive: true })
}
await mkdir(outDir, { recursive: true })

// Build React application with Bun
const buildResult = await Bun.build({
  entrypoints: [join(import.meta.dir, "src/main.tsx")],
  outdir: outDir,
  minify: true,
  splitting: false,
  target: "browser",
  format: "esm",
  naming: {
    entry: "[name].[ext]",
    chunk: "[name].[ext]",
    asset: "[name].[ext]"
  }
})

if (!buildResult.success) {
  console.error("Build failed:")
  for (const log of buildResult.logs) {
    console.error(log)
  }
  process.exit(1)
}

const cssInput = join(import.meta.dir, "src/styles.css")
const cssOutput = join(outDir, "styles.css")

const cssProcess = Bun.spawn([
  "bunx",
  "@tailwindcss/cli",
  "-i", cssInput,
  "-o", cssOutput,
  "--minify"
], {
  cwd: import.meta.dir,
  stdout: "inherit",
  stderr: "inherit"
})

await cssProcess.exited

// Create HTML file
const html = `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Aionify - Time Tracking</title>
  <link rel="stylesheet" href="/styles.css">
</head>
<body>
  <div id="root"></div>
  <script type="module" src="/main.js"></script>
</body>
</html>`

await Bun.write(join(outDir, "index.html"), html)

console.log("Build complete! Output in dist/")
console.log("Generated files:")
for (const output of buildResult.outputs) {
  console.log(`  - ${output.path.split("/").pop()}`)
}
console.log("  - index.html")
console.log("  - styles.css")
