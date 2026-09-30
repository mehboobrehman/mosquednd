/**
 * dev-harness/errorInterceptor.ts
 * 
 * Global Dev Error Interceptor for MosqueDND.
 * Automatically hooks into React Native and global JS error handlers in development
 * to report unhandled crashes directly to the telemetry server.
 */

import { logger } from './logger';

declare const __DEV__: boolean;

let isInstalled = false;
let originalGlobalHandler: any = null;
let originalOnError: any = null;
let originalOnUnhandledRejection: any = null;

// React Native global error handler type
interface ErrorUtilsType {
  setGlobalHandler(callback: (error: Error, isFatal?: boolean) => void): void;
  getGlobalHandler(): ((error: Error, isFatal?: boolean) => void) | undefined;
}

const getGlobalErrorUtils = (): ErrorUtilsType | null => {
  return (global as any).ErrorUtils || null;
};

/**
 * Initializes the global error interceptors.
 * Active only in development mode.
 */
export function initErrorInterceptor() {
  const isDev = typeof __DEV__ !== 'undefined' ? __DEV__ : process.env.NODE_ENV !== 'production';
  if (!isDev || isInstalled) {
    return;
  }

  isInstalled = true;
  logger.i('Initializing global dev error interceptors', 'ErrorInterceptor');

  // 1. React Native specific global exception handler
  const ErrorUtils = getGlobalErrorUtils();
  if (ErrorUtils) {
    originalGlobalHandler = ErrorUtils.getGlobalHandler();
    ErrorUtils.setGlobalHandler((error: Error, isFatal?: boolean) => {
      logger.log(
        isFatal ? 'FATAL' : 'ERROR',
        `Uncaught RN Error: ${error.message}`,
        error.stack,
        'ErrorUtils'
      );
      // Delegate to original handler so React Native crash screen (LogBox) still displays
      if (originalGlobalHandler) {
        originalGlobalHandler(error, isFatal);
      }
    });
  }

  // 2. Browser/Web-view global error handler (window.onerror)
  if (typeof window !== 'undefined') {
    originalOnError = window.onerror;
    window.onerror = (message, source, lineno, colno, error) => {
      logger.log(
        'FATAL',
        `window.onerror: ${message} at ${source}:${lineno}:${colno}`,
        error?.stack,
        'WebWindow'
      );
      if (originalOnError) {
        return originalOnError(message, source, lineno, colno, error);
      }
      return false;
    };

    // 3. Unhandled promise rejections
    originalOnUnhandledRejection = (window as any).onunhandledrejection;
    (window as any).onunhandledrejection = (event: PromiseRejectionEvent) => {
      const reason = event.reason;
      const stack = reason instanceof Error ? reason.stack : undefined;
      const message = reason instanceof Error ? reason.message : String(reason);

      logger.log(
        'ERROR',
        `Unhandled Promise Rejection: ${message}`,
        stack,
        'PromiseRejection'
      );

      if (originalOnUnhandledRejection) {
        originalOnUnhandledRejection(event);
      }
    };
  } else if (typeof process !== 'undefined') {
    // Node.js or similar command-line runner environment
    process.on('unhandledRejection', (reason: any) => {
      const stack = reason instanceof Error ? reason.stack : undefined;
      const message = reason instanceof Error ? reason.message : String(reason);

      logger.log(
        'ERROR',
        `Unhandled Promise Rejection: ${message}`,
        stack,
        'PromiseRejection'
      );
    });
  }
}

/**
 * Teardown and restore the original global error handlers.
 */
export function uninstallErrorInterceptor() {
  if (!isInstalled) return;

  const ErrorUtils = getGlobalErrorUtils();
  if (ErrorUtils && originalGlobalHandler) {
    ErrorUtils.setGlobalHandler(originalGlobalHandler);
  }

  if (typeof window !== 'undefined') {
    if (originalOnError) {
      window.onerror = originalOnError;
    }
    if (originalOnUnhandledRejection) {
      (window as any).onunhandledrejection = originalOnUnhandledRejection;
    }
  }

  isInstalled = false;
  logger.i('Uninstalled global error interceptors', 'ErrorInterceptor');
}
