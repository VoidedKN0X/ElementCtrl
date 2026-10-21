/*
Copyright 2021-2024 New Vector Ltd.

SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
Please see LICENSE in the repository root for full details.

ElementCtrl: the sign-up page is removed (with its reCAPTCHA, Google privacy policy
and terms links). ElementCtrl only uses Element Call as a widget inside the app,
where you're already signed in; anything that still links here goes home instead.
*/

import { type FC } from "react";
import { Navigate } from "react-router-dom";

export const RegisterPage: FC = () => <Navigate to="/" replace />;
