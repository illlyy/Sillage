import Module from "node:module";
import path from "node:path";

export function installModuleAliasHook(): void {
  const moduleWithLoad = Module as typeof Module & {
    _load: (
      request: string,
      parent: NodeModule | undefined,
      isMain: boolean,
    ) => unknown;
  };
  const originalLoad = moduleWithLoad._load;

  moduleWithLoad._load = function moduleAliasLoad(
    request: string,
    parent: NodeModule | undefined,
    isMain: boolean,
  ): unknown {
    if (request === "electron") {
      return originalLoad.call(this, path.resolve(
        path.resolve(__dirname, "../.."),
        "src/server/electron/index.js",
      ), parent, isMain);
    }

    return originalLoad.call(this, request, parent, isMain);
  };
}
