/*
ElementCtrl's build of Element Call: the upstream "embedded" build
(vite-embedded.config.ts, what io.element.android:element-call-embedded ships),
with the libraries ElementCtrl never uses replaced by local stand-ins so they
are not part of the bundle at all:

- @sentry/react      -> src/elementctrl/sentry.tsx (a plain React error boundary)
- posthog-js         -> src/elementctrl/posthog.ts (does nothing)
- @livekit/track-processors -> src/elementctrl/track-processors.ts (blur unsupported)
- @mediapipe/tasks-vision   -> src/elementctrl/empty.ts

Source maps are not generated: they aren't shipped in the app.

SPDX-License-Identifier: AGPL-3.0-only
*/

import { fileURLToPath } from "node:url";
import { defineConfig, mergeConfig } from "vite";

import embeddedConfig from "./vite-embedded.config.ts";

const stub = (file: string): string =>
  fileURLToPath(new URL(`./src/elementctrl/${file}`, import.meta.url));

export default defineConfig((env) =>
  mergeConfig(
    embeddedConfig(env),
    defineConfig({
      resolve: {
        alias: [
          { find: /^@sentry\/react$/, replacement: stub("sentry.tsx") },
          { find: /^posthog-js$/, replacement: stub("posthog.ts") },
          {
            find: /^@livekit\/track-processors$/,
            replacement: stub("track-processors.ts"),
          },
          { find: /^@mediapipe\/tasks-vision$/, replacement: stub("empty.ts") },
        ],
      },
      build: {
        sourcemap: false,
      },
    }),
  ),
);
