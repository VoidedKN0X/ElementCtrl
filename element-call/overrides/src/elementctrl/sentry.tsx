/*
ElementCtrl: stands in for @sentry/react in ElementCtrl's build of Element Call.

Crash reporting is never used in ElementCtrl, so Sentry's SDK is left out of the
bundle entirely. The error boundary is kept (Element Call relies on it to show its
error screens), as a plain React error boundary that reports nothing anywhere.

SPDX-License-Identifier: AGPL-3.0-only
*/

import {
  Component,
  type ComponentType,
  type ErrorInfo,
  type ReactElement,
  type ReactNode,
} from "react";

export type FallbackRender = (errorData: {
  error: unknown;
  componentStack: string;
  eventId: string;
  resetError(): void;
}) => ReactElement;

interface ErrorBoundaryProps {
  children?: ReactNode | (() => ReactNode);
  fallback?: ReactElement | FallbackRender;
  onError?: (error: unknown, componentStack: string, eventId: string) => void;
  onReset?: (error: unknown, componentStack: string | null, eventId: string | null) => void;
}

interface ErrorBoundaryState {
  error: unknown;
  componentStack: string | null;
  hasError: boolean;
}

export class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  public state: ErrorBoundaryState = { error: null, componentStack: null, hasError: false };

  public static getDerivedStateFromError(error: unknown): Partial<ErrorBoundaryState> {
    return { error, hasError: true };
  }

  public componentDidCatch(error: unknown, info: ErrorInfo): void {
    const componentStack = info.componentStack ?? "";
    this.setState({ componentStack });
    this.props.onError?.(error, componentStack, "");
  }

  private resetError = (): void => {
    const { error, componentStack } = this.state;
    this.props.onReset?.(error, componentStack, null);
    this.setState({ error: null, componentStack: null, hasError: false });
  };

  public render(): ReactNode {
    if (this.state.hasError) {
      const { fallback } = this.props;
      if (typeof fallback === "function") {
        return fallback({
          error: this.state.error,
          componentStack: this.state.componentStack ?? "",
          eventId: "",
          resetError: this.resetError,
        });
      }
      return fallback ?? null;
    }
    const { children } = this.props;
    return typeof children === "function" ? children() : (children ?? null);
  }
}

export function withSentryReactRouterV7Routing<P extends object>(
  Route: ComponentType<P>,
): ComponentType<P> {
  return Route;
}

export const withSentryReactRouterV6Routing = withSentryReactRouterV7Routing;

export function reactRouterV7BrowserTracingIntegration(..._args: unknown[]): object {
  return {};
}

export const reactRouterV6BrowserTracingIntegration = reactRouterV7BrowserTracingIntegration;

export function init(..._args: unknown[]): void {}

export function captureException(..._args: unknown[]): string {
  return "";
}

export function captureMessage(..._args: unknown[]): string {
  return "";
}

export function setUser(..._args: unknown[]): void {}

export function setTag(..._args: unknown[]): void {}

export function setContext(..._args: unknown[]): void {}

export function addBreadcrumb(..._args: unknown[]): void {}
