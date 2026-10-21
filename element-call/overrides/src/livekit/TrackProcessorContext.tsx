/*
Copyright 2024-2025 New Vector Ltd.

SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
Please see LICENSE in the repository root for full details.

ElementCtrl: background blur removed. Upstream, blur uses Google's MediaPipe
(@mediapipe/tasks-vision and a segmentation model) through @livekit/track-processors,
and is only offered on desktop browsers, so it never worked in ElementCtrl on a
phone. This keeps the same interface with blur always unsupported, so neither
MediaPipe nor the track processors are part of the build.
*/

import { createContext, type FC, type JSX, use } from "react";
import { type LocalVideoTrack } from "livekit-client";
import { logger } from "matrix-js-sdk/lib/logger";
import { combineLatest } from "rxjs";

import { type Behavior } from "../state/Behavior";
import { type ObservableScope } from "../state/ObservableScope";
import { useValueBehavior } from "../useValueBehavior";

export type ProcessorState = {
  supported: boolean | undefined;
  processor: undefined;
};

const unsupported: ProcessorState = { supported: false, processor: undefined };

const ProcessorContext = createContext<ProcessorState | undefined>(undefined);

export function useTrackProcessor(): ProcessorState {
  const state = use(ProcessorContext);
  if (state === undefined)
    throw new Error(
      "useTrackProcessor must be used within a ProcessorProvider",
    );
  return state;
}

export function useTrackProcessorState$(): Behavior<ProcessorState> {
  return useValueBehavior(useTrackProcessor());
}

/** There is never a processor to attach; detaches one if a track somehow has it. */
export function applyProcessor(
  videoTrack: LocalVideoTrack,
  _processor: undefined,
): void {
  if (videoTrack.getProcessor()) {
    videoTrack.stopProcessor().catch((e) => {
      logger.warn("Failed to stop video processor", e);
    });
  }
}

export const trackProcessorSync = (
  scope: ObservableScope,
  videoTrack$: Behavior<LocalVideoTrack | null>,
  processor$: Behavior<ProcessorState>,
): void => {
  combineLatest([videoTrack$, processor$])
    .pipe(scope.bind())
    .subscribe(([videoTrack, processorState]) => {
      if (!processorState) return;
      if (!videoTrack) return;
      applyProcessor(videoTrack, undefined);
    });
};

export const useTrackProcessorSync = (
  _videoTrack: LocalVideoTrack | null,
): void => {};

interface Props {
  children: JSX.Element;
}

export const ProcessorProvider: FC<Props> = ({ children }) => (
  <ProcessorContext value={unsupported}>{children}</ProcessorContext>
);
