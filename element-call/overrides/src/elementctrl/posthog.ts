/*
ElementCtrl: stands in for posthog-js in ElementCtrl's build of Element Call.

ElementCtrl has no analytics, so PostHog's SDK (with its crawler lists, campaign
and search-engine detection) is left out of the bundle entirely. Every call does
nothing and nothing is ever sent.

SPDX-License-Identifier: AGPL-3.0-only
*/

const noop = (): void => {};

const posthog = new Proxy(
  {},
  {
    get: (_target, prop): unknown => (prop === "then" ? undefined : noop),
  },
) as Record<string, (...args: unknown[]) => unknown>;

export default posthog;
export { posthog };
