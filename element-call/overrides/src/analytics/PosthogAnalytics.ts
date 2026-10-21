/*
Copyright 2022-2024 New Vector Ltd.

SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
Please see LICENSE in the repository root for full details.

ElementCtrl: analytics removed. This keeps Element Call's analytics interface so
the rest of the app is unchanged, but every method does nothing and PostHog's SDK
is not part of the build. Nothing is collected, stored or sent.
*/

export interface IPosthogEvent {
  eventName: string;
  $set?: void;
  $set_once?: void;
}

export enum Anonymity {
  Disabled,
  Anonymous,
  Pseudonymous,
}

export enum RegistrationType {
  Guest,
  Registered,
}

export interface AnalyticsConfig {
  apiKey?: string;
  apiHost?: string;
  matrixBackend: "embedded" | "jssdk";
  hostAnalyticsId?: string | null;
}

export function santizeSensitiveData<T>(event: T, _anonymity: Anonymity): T {
  return event;
}

const noop = (): void => {};

/** An event tracker that never records or sends anything. */
const tracker = {
  track: noop,
  cacheStartCall: noop,
  cacheParticipantCountChanged: noop,
  cacheReconnecting: noop,
  cacheSignupStart: noop,
  cacheSignupEnd: noop,
  getSignupEndTime: (): Date => new Date(0),
  cacheConnectStart: noop,
  cacheWsConnect: noop,
};

export class PosthogAnalytics {
  private static internalInstance: PosthogAnalytics | null = null;

  public static configure(_config: AnalyticsConfig): void {}

  public static hasInstance(): boolean {
    return Boolean(this.internalInstance);
  }

  public static get instance(): PosthogAnalytics {
    this.internalInstance ??= new PosthogAnalytics();
    return this.internalInstance;
  }

  public static resetInstance(): void {
    this.internalInstance = null;
  }

  private constructor() {}

  public isEnabled(): boolean {
    return false;
  }

  public getAnonymity(): Anonymity {
    return Anonymity.Disabled;
  }

  public logout(): void {}

  public onLoginStatusChanged(): void {}

  public trackEvent(_event: IPosthogEvent, _options?: unknown): void {}

  public startListeningToSettingsChanges(): void {}

  public setRegistrationType(_registrationType: RegistrationType): void {}

  public eventCallEnded = tracker;
  public eventSignup = tracker;
  public eventCallStarted = tracker;
  public eventLogin = tracker;
  public eventMuteMicrophone = tracker;
  public eventMuteCamera = tracker;
  public eventUndecryptableToDevice = tracker;
  public eventQualitySurvey = tracker;
  public eventCallDisconnected = tracker;
  public eventCallConnectDuration = tracker;
  public eventCallReconnecting = tracker;
}
