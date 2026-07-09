import { rmSync } from "node:fs";
import { resolve, sep } from "node:path";

const staticDir = resolve(process.cwd(), "../backend/src/main/resources/static");
const assetsDir = resolve(staticDir, "assets");

if (!assetsDir.startsWith(staticDir + sep)) {
  throw new Error(`Refusing to clean assets outside backend static directory: ${assetsDir}`);
}

rmSync(assetsDir, { recursive: true, force: true });
