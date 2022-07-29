/*
 * Copyright © 2022 JINSPIRED B.V.
 */

package io.humainary.devkit.perfkit;

import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.results.AverageTimeResult;
import org.openjdk.jmh.results.BenchmarkResult;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import java.io.IOException;
import java.util.Collection;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import static java.lang.System.getProperty;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static org.openjdk.jmh.runner.options.TimeValue.seconds;

public final class PerfKit {

  /**
   * The Benchmark marker interface.
   */

  public interface Driver {

    String ALL = "*";

    void setup () throws Exception;

    default UnaryOperator< String > configuration ()
    throws IOException {

      final var properties =
        new Properties ();

      properties.load (
        getClass ()
          .getResourceAsStream (
            "/" +
              getProperty (
                "io.humainary.devkit.perfkit.profile",
                "spi"
              ) + ".properties"
          )
      );

      return
        properties::getProperty;

    }

  }

  public static Target target (
    final Class< ? extends Driver > benchmark,
    final String api,
    final String spi
  ) {

    return
      new Target (
        benchmark,
        api,
        spi
      );

  }

  public static final class Target {

    private final Class< ? extends Driver > benchmark;
    private final String                    api;
    private final String                    spi;

    private Target (
      final Class< ? extends Driver > benchmark,
      final String api,
      final String spi
    ) {

      this.benchmark = benchmark;
      this.api = api;
      this.spi = spi;

    }

  }


  public static void execute (
    final Target target,
    final String profile,
    final String pattern,
    final int threads,
    final double threshold,
    final Consumer< ? super String > consumer
  ) {

    try {

      Launcher.inspect (
        target,
        Launcher.run (
          Launcher.options (
            target,
            profile,
            pattern,
            threads
          )
        ),
        threshold,
        consumer
      );

    } catch (
      final Throwable error
    ) {

      consumer.accept (
        error.toString ()
      );

    }

  }

  private static final class Launcher {

    private static final String    NEWLINE         = getProperty ( "line.separator" );
    private static final char      SEMICOLON       = ':';
    private static final char      SPACE           = ' ';
    private static final int       ITERATIONS      = 2;
    private static final TimeValue TIME            = seconds ( 2L );
    private static final TimeValue WARMUP_TIME     = seconds ( 1L );
    private static final int       WARM_ITERATIONS = 2;
    private static final boolean   GC_ENABLED      = true;
    private static final int       FORK_COUNT      = 2;
    private static final boolean   FAIL_ON_ERROR   = true;

    private static Collection< RunResult > run (
      final Options options
    ) throws RunnerException {

      return
        new Runner (
          options
        ).run ();

    }

    private static Options options (
      final Target target,
      final String profile,
      final String pattern,
      final int threads
    ) {

      return
        new OptionsBuilder ()
          .include ( target.benchmark.getSimpleName () + "." + pattern )
          .mode ( Mode.AverageTime )
          .timeUnit ( NANOSECONDS )
          .warmupTime ( WARMUP_TIME )
          .warmupIterations ( WARM_ITERATIONS )
          .measurementTime ( TIME )
          .measurementIterations ( ITERATIONS )
          .threads ( threads )
          .shouldFailOnError ( FAIL_ON_ERROR )
          .shouldDoGC ( GC_ENABLED )
          .forks ( FORK_COUNT )
          .jvmArgsAppend (
            "-server",
            "-Dio.humainary." + target.api + ".spi.factory=" + target.spi,
            "-D" + "io.humainary.devkit.perfkit.profile" + "=" + profile
          ).build ();

    }

    private static void inspect (
      final Target target,
      final Collection< ? extends RunResult > results,
      final double threshold,
      final Consumer< ? super String > consumer
    ) {

      final var list =
        results
          .stream ()
          .flatMap (
            run ->
              run.getBenchmarkResults ().stream () )
          .map ( BenchmarkResult::getPrimaryResult )
          .map ( AverageTimeResult.class::cast )
          .filter (
            result ->
              result.getScore () > threshold
          ).toList ();

      if ( !list.isEmpty () ) {

        final var message =
          new StringBuilder ( list.size () * 100 );

        message.append (
          "provider"
        ).append (
          SEMICOLON
        ).append (
          SPACE
        ).append (
          target.spi
        ).append (
          NEWLINE
        );

        list.forEach (
          result ->
            message.append (
              result.getLabel ()
            ).append (
              SEMICOLON
            ).append (
              SPACE
            ).append (
              result.getScore ()
            ).append (
              NEWLINE
            )
        );

        consumer.accept (
          message.toString ()
        );

      }

    }

  }

}
