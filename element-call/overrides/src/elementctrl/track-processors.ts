/*
ElementCtrl: stands in for @livekit/track-processors in ElementCtrl's build of
Element Call.

Background blur needs Google's MediaPipe (a WebAssembly runtime and a segmentation
model, which LiveKit's package otherwise fetches from storage.googleapis.com and
cdn.jsdelivr.net). Element Call only offers blur on desktop browsers, never on a
phone, so it was never available in ElementCtrl: the processors and MediaPipe are
left out of the bundle entirely and blur is reported as unsupported.

SPDX-License-Identifier: AGPL-3.0-only
*/

export const supportsBackgroundProcessors = (): boolean => false;

export const supportsModernBackgroundProcessors = (): boolean => false;

export type BackgroundOptions = Record<string, unknown>;

export class ProcessorWrapper<T = BackgroundOptions> {
  public constructor(..._args: unknown[]) {
    throw new Error("Background processors are not available in ElementCtrl");
  }

  public static isSupported = false;

  public async updateTransformerOptions(_options: T): Promise<void> {}
}

export class VideoTransformer {}

export class BackgroundTransformer extends VideoTransformer {}

export const BackgroundBlur = (): never => {
  throw new Error("Background processors are not available in ElementCtrl");
};

export const VirtualBackground = BackgroundBlur;
