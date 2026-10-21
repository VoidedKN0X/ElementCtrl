/*
Copyright 2022-2024 New Vector Ltd.

SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
Please see LICENSE in the repository root for full details.

ElementCtrl: reCAPTCHA removed. Upstream, signing up for an account on Element
Call's own pages loads Google's reCAPTCHA script. ElementCtrl only uses Element
Call as a widget inside the app, where you're already signed in, so it never
signs anyone up: this never loads any script and answers every captcha with an
empty response (a server that requires one refuses the sign-up).
*/

import { useState } from "react";

const noResponse = async (): Promise<string> => "";
const noop = (): void => {};

export function useRecaptcha(_sitekey?: string): {
  execute: () => Promise<string>;
  reset: () => void;
  recaptchaId: string;
} {
  const [recaptchaId] = useState("elementctrl-no-captcha");
  return { execute: noResponse, reset: noop, recaptchaId };
}
