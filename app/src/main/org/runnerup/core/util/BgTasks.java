package org.runnerup.core.util;

import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/** Background work replacement for deprecated {@link android.os.AsyncTask}. */
public final class BgTasks {

  public interface ProgressPublisher {
    void publish(String message);
  }

  private static final ExecutorService DB_EXECUTOR = Executors.newSingleThreadExecutor();
  private static final ExecutorService NETWORK_EXECUTOR = Executors.newCachedThreadPool();
  private static final Handler MAIN = new Handler(Looper.getMainLooper());

  private BgTasks() {}

  public static void run(Runnable background, Runnable onComplete) {
    runDb(background, onComplete);
  }

  public static void run(Runnable background) {
    run(background, null);
  }

  public static void runDb(Runnable background, Runnable onComplete) {
    DB_EXECUTOR.execute(
        () -> {
          try {
            background.run();
          } finally {
            if (onComplete != null) {
              MAIN.post(onComplete);
            }
          }
        });
  }

  public static <T> void runDb(Supplier<T> background, Consumer<T> onComplete) {
    runOnExecutor(DB_EXECUTOR, background, onComplete, null);
  }

  public static <T> void runNetwork(Supplier<T> background, Consumer<T> onComplete) {
    runOnExecutor(NETWORK_EXECUTOR, background, onComplete, null);
  }

  /** Runs on the network executor and blocks until complete or timeout. */
  public static <T> T runNetworkBlocking(Supplier<T> background, long timeout, TimeUnit unit)
      throws ExecutionException, InterruptedException, TimeoutException {
    Future<T> future = NETWORK_EXECUTOR.submit(background::get);
    return future.get(timeout, unit);
  }

  public static <T> void runDbWithProgress(
      Function<ProgressPublisher, T> background,
      Consumer<T> onComplete,
      Consumer<String> onProgress) {
    runOnExecutor(DB_EXECUTOR, null, onComplete, onProgress, background);
  }

  public static <T> void runNetworkWithProgress(
      Function<ProgressPublisher, T> background,
      Consumer<T> onComplete,
      Consumer<String> onProgress) {
    runOnExecutor(NETWORK_EXECUTOR, null, onComplete, onProgress, background);
  }

  private static <T> void runOnExecutor(
      ExecutorService executor,
      Supplier<T> supplier,
      Consumer<T> onComplete,
      Consumer<String> onProgress) {
    runOnExecutor(executor, supplier, onComplete, onProgress, null);
  }

  private static <T> void runOnExecutor(
      ExecutorService executor,
      Supplier<T> supplier,
      Consumer<T> onComplete,
      Consumer<String> onProgress,
      Function<ProgressPublisher, T> function) {
    executor.execute(
        () -> {
          ProgressPublisher publisher =
              message -> {
                if (message != null && onProgress != null) {
                  MAIN.post(() -> onProgress.accept(message));
                }
              };
          T result = function != null ? function.apply(publisher) : supplier.get();
          if (onComplete != null) {
            MAIN.post(() -> onComplete.accept(result));
          }
        });
  }
}
