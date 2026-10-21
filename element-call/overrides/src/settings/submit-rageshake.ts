/*
Copyright 2022-2024 New Vector Ltd.

SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
Please see LICENSE in the repository root for full details.

ElementCtrl: bug-report (rageshake) upload removed. Upstream, this collects debug
logs, device and encryption details and uploads them to a rageshake server. In
ElementCtrl nothing is ever uploaded: there is no upload code, reports are never
offered ("available" is always false) and requests for logs from other people in
the call are ignored.
*/

import { type ComponentProps, useCallback } from "react";

import { type RageshakeRequestModal } from "../room/RageshakeRequestModal";

interface RageShakeSubmitOptions {
  sendLogs: boolean;
  rageshakeRequestId?: string;
  description?: string;
  roomId?: string;
  label?: string;
}

export function getRageshakeSubmitUrl(): string | undefined {
  return undefined;
}

export function useSubmitRageshake(
  _injectedGetRageshakeSubmitUrl = getRageshakeSubmitUrl,
): {
  submitRageshake: (opts: RageShakeSubmitOptions) => Promise<void>;
  sending: boolean;
  sent: boolean;
  error?: Error;
  available: boolean;
} {
  const submitRageshake = useCallback(
    async (_opts: RageShakeSubmitOptions): Promise<void> => {
      throw new Error("Bug reports are not available in ElementCtrl");
    },
    [],
  );
  return {
    submitRageshake,
    sending: false,
    sent: false,
    error: undefined,
    available: false,
  };
}

export function useRageshakeRequest(): (
  roomId: string,
  rageshakeRequestId: string,
) => void {
  return useCallback((_roomId: string, _rageshakeRequestId: string) => {}, []);
}

const noop = (): void => {};

export function useRageshakeRequestModal(
  roomId: string,
): ComponentProps<typeof RageshakeRequestModal> {
  return {
    rageshakeRequestId: "",
    roomId,
    open: false,
    onDismiss: noop,
  };
}
