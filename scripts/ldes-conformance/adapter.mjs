import { spawn } from "node:child_process";
import { readFileSync, writeSync } from "node:fs";
import path from "node:path";
import {
  exists,
  resetState,
  writeDocument,
} from "../common.mjs";

const operation = process.argv[2];
const jar = path.resolve(
  process.env.LDES_CT_OPENLDES_ADAPTER_JAR ??
    "adapters/openldes-ldi/target/openldes-ldi-adapter.jar",
);

if (operation === "probe") {
  const available = await exists(jar);
  writeSync(1, `${JSON.stringify({
      protocolVersion: 0,
      available,
      adapterId: "openldes-ldi",
      clientVersion: "3.1.1",
      clientRevision: process.env.LDES_CT_CLIENT_REVISION ?? "unknown",
      capabilities: {
        modes: ["unordered", "ordered"],
        memberExtraction: true,
        context: true,
        persistentState: true,
      },
      reason: available
        ? undefined
        : `adapter host JAR not found (expected ${path.relative(process.cwd(), jar)}); run adapters/openldes-ldi/build.sh`,
    })}\n`);
} else if (operation === "reset") {
  await resetState(readInputSync());
} else if (operation === "run") {
  const input = readInputSync();
  await forwardJava(jar, input);
} else {
  throw new Error(`unknown operation: ${operation}`);
}

function readInputSync() {
  const source = readFileSync(0, "utf8");
  return source.trim() ? JSON.parse(source) : {};
}

async function forwardJava(jarPath, input) {
  await new Promise((resolve, reject) => {
    const child = spawn("java", ["-jar", jarPath], {
      stdio: ["pipe", "inherit", "inherit"],
    });
    child.on("error", reject);
    child.on("close", (code) =>
      code === 0 ? resolve() : reject(new Error(`Java host exited with ${code}`)),
    );
    child.stdin.end(`${JSON.stringify(input)}\n`);
  });
}
