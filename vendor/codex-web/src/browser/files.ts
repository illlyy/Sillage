import { emitRendererEvent, isRecord } from "./shim";

type CodexFetchMessage = {
  body?: string;
  headers?: Record<string, string>;
  hostId?: string;
  method: string;
  requestId: string;
  type: "fetch";
  url: string;
};

type PickFilesRequest = {
  imagesOnly?: boolean;
  pickerTitle?: string;
};

function openBrowserFilePicker({
  allowMultiple,
  imagesOnly,
}: {
  allowMultiple: boolean;
  imagesOnly?: boolean;
}): Promise<File[]> {
  return new Promise((resolve, reject) => {
    const input = document.createElement("input");
    let settled = false;

    function cleanup(): void {
      input.removeEventListener("cancel", handleCancel);
      input.removeEventListener("change", handleChange);
      input.remove();
    }

    function finish(files: File[]): void {
      if (settled) {
        return;
      }
      settled = true;
      cleanup();
      resolve(files);
    }

    function fail(error: unknown): void {
      if (settled) {
        return;
      }
      settled = true;
      cleanup();
      reject(error);
    }

    function handleCancel(): void {
      finish([]);
    }

    function handleChange(): void {
      finish(Array.from(input.files ?? []));
    }

    input.type = "file";
    input.multiple = allowMultiple;
    if (imagesOnly) {
      input.accept = "image/*";
    }
    Object.assign(input.style, {
      height: "1px",
      left: "-9999px",
      opacity: "0",
      position: "fixed",
      top: "0",
      width: "1px",
    });
    input.addEventListener("cancel", handleCancel);
    input.addEventListener("change", handleChange);
    document.body.append(input);

    try {
      input.click();
    } catch (error) {
      fail(error);
    }
  });
}


function fileToBase64(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onerror = () => reject(reader.error ?? new Error("Failed to read file"));
    reader.onload = () => resolve(String(reader.result ?? "").replace(/^data:[^,]*,/, ""));
    reader.readAsDataURL(file);
  });
}

async function uploadFiles(files: File[]) {
  if (files.length === 0) {
    return [];
  }

  const native = (window as typeof window & { CodexDesktopNative?: { uploadFiles?: (json: string) => string } }).CodexDesktopNative;
  if (native?.uploadFiles) {
    const payload = await Promise.all(files.map(async (file) => ({
      name: file.name || "upload",
      type: file.type || "application/octet-stream",
      data: await fileToBase64(file),
    })));
    return JSON.parse(native.uploadFiles(JSON.stringify(payload))).files;
  }

  const uploadUrl = new URL("/__backend/upload", window.location.href);
  const formData = new FormData();

  for (const file of files) {
    formData.append("files", file, file.name || "upload");
  }

  const response = await fetch(uploadUrl, {
    method: "POST",
    body: formData,
  });

  if (!response.ok) {
    throw new Error(`Upload failed: ${response.status} ${response.statusText}`);
  }

  return (await response.json()).files;
}


type AndroidSharedFile = {
  data: string;
  name: string;
  type: string;
};

declare global {
  interface Window {
    __codexAndroidShare?: (payloadJson: string) => Promise<boolean>;
  }
}

function decodeSharedFile(file: AndroidSharedFile): File {
  const binary = atob(file.data);
  const bytes = new Uint8Array(binary.length);
  for (let index = 0; index < binary.length; index += 1) {
    bytes[index] = binary.charCodeAt(index);
  }
  return new File([bytes], file.name || "shared-file", {
    type: file.type || "application/octet-stream",
  });
}

function composerDropTarget(): HTMLElement | null {
  const focused = document.activeElement;
  if (focused instanceof HTMLElement && focused.isContentEditable) return focused;
  return document.querySelector<HTMLElement>(
    '[contenteditable="true"], textarea[data-composer-navigation-target], textarea',
  );
}

async function importAndroidShare(payloadJson: string): Promise<boolean> {
  const native = (window as typeof window & {
    CodexDesktopNative?: { readSharedFiles?: (json: string) => string };
  }).CodexDesktopNative;
  if (!native?.readSharedFiles) return false;

  const decoded = JSON.parse(native.readSharedFiles(payloadJson)) as {
    files?: AndroidSharedFile[];
    text?: string;
  };
  let target = composerDropTarget();
  for (let attempt = 0; target == null && attempt < 20; attempt += 1) {
    await new Promise((resolve) => window.setTimeout(resolve, 250));
    target = composerDropTarget();
  }
  if (!target) return false;
  target.focus();

  if (decoded.files?.length) {
    const transfer = new DataTransfer();
    for (const file of decoded.files) transfer.items.add(decodeSharedFile(file));
    target.dispatchEvent(
      new DragEvent("drop", {
        bubbles: true,
        cancelable: true,
        dataTransfer: transfer,
      }),
    );
  }

  if (decoded.text) {
    const transfer = new DataTransfer();
    transfer.setData("text/plain", decoded.text);
    target.dispatchEvent(
      new ClipboardEvent("paste", {
        bubbles: true,
        cancelable: true,
        clipboardData: transfer,
      }),
    );
  }
  return Boolean(decoded.files?.length || decoded.text);
}

window.__codexAndroidShare = importAndroidShare;

export async function handleLocalFilePickerMessage(message: CodexFetchMessage) {
  try {
    const response = await handleLocalFilePickerMessageInner(message);

    sendFetchResponse(message, {
      responseType: "success",
      body: response,
    });
  } catch (error) {
    console.error(error);

    sendFetchResponse(message, {
      responseType: "error",
      status: 432,
      error: errorMessage(error),
    });
  }
}

async function handleLocalFilePickerMessageInner(message: CodexFetchMessage) {
  const request = parsePickFilesRequest(message);
  const allowMultiple = message.url === "vscode://codex/pick-files";

  const selectedFiles = await openBrowserFilePicker({
    allowMultiple,
    imagesOnly: request.imagesOnly,
  });

  const uploadedFiles = await uploadFiles(selectedFiles);

  return allowMultiple
    ? { files: uploadedFiles }
    : { file: uploadedFiles[0] ?? null };
}

function isCodexFetchMessage(value: unknown): value is CodexFetchMessage {
  return isRecord(value) && value.type === "fetch";
}

export function isLocalFilePickerMessage(
  value: unknown,
): value is CodexFetchMessage {
  return (
    isCodexFetchMessage(value) &&
    value.method.toUpperCase() === "POST" &&
    (value.url === "vscode://codex/pick-files" ||
      value.url === "vscode://codex/pick-file")
  );
}

function parsePickFilesRequest(message: CodexFetchMessage): PickFilesRequest {
  if (!message.body) {
    return {};
  }

  try {
    const parsed = JSON.parse(message.body) as unknown;
    if (!isRecord(parsed)) {
      return {};
    }
    return {
      imagesOnly:
        typeof parsed.imagesOnly === "boolean" ? parsed.imagesOnly : undefined,
      pickerTitle:
        typeof parsed.pickerTitle === "string" ? parsed.pickerTitle : undefined,
    };
  } catch {
    return {};
  }
}

function sendFetchResponse(
  message: CodexFetchMessage,
  response:
    | {
        responseType: "success";
        body: unknown;
        status?: number;
      }
    | {
        responseType: "error";
        error: string;
        status?: number;
      },
): void {
  const payload =
    response.responseType === "success"
      ? {
          type: "fetch-response",
          responseType: "success",
          requestId: message.requestId,
          status: response.status ?? 200,
          headers: { "content-type": "application/json" },
          bodyJsonString: JSON.stringify(response.body),
        }
      : {
          type: "fetch-response",
          responseType: "error",
          requestId: message.requestId,
          status: response.status ?? 432,
          error: response.error,
        };

  emitRendererEvent("codex_desktop:message-for-view", [payload]);
}

function errorMessage(error: unknown): string {
  if (error instanceof Error) {
    return error.message;
  }
  return String(error);
}
