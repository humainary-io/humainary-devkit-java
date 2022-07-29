/*
 * Copyright © 2022 JINSPIRED B.V.
 */

package io.humainary.devkit.testkit;

import io.humainary.substrates.Substrates.*;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.lang.Math.max;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.Optional.ofNullable;
import static java.util.Spliterator.*;
import static java.util.function.UnaryOperator.identity;

/**
 * The TestKit utility class with recording capabilities.
 *
 * @author wlouth
 * @version 1.0
 */

public final class TestKit {

  private TestKit () {}

  /**
   * An extension of the {@link Subscriber} interface that records event captures.
   */

  public interface Recorder< R > {

    /**
     * Starts recording event captures.
     */

    void start ();

    /**
     * Stops recording and returns a linked list of captures.
     *
     * @return The last recorded capture or {@code Optional.empty()}
     */

    Optional< Capture< R > > stop ();

  }

  /**
   * Returns a new recorder instance.
   *
   * @return A new recorder instance.
   */

  public static < I, R > Recorder< R > recorder (
    final Source< ? extends I > context,
    final Function< ? super I, ? extends R > mapper
  ) {

    return
      new CaptureRecorder<> (
        context,
        __ -> true,
        mapper
      );

  }

  /**
   * Returns a new recorder instance.
   *
   * @return A new recorder instance.
   */

  public static < E > Recorder< E > recorder (
    final Source< ? extends E > context
  ) {

    return
      new CaptureRecorder<> (
        context,
        __ -> true,
        identity ()
      );

  }


  /**
   * Returns a new recorder instance.
   *
   * @return A new recorder instance.
   */

  public static < E, R > Recorder< R > recorder (
    final Source< ? extends E > context,
    final Predicate< ? super E > filter,
    final Function< ? super E, ? extends R > mapper
  ) {

    return
      new CaptureRecorder<> (
        context,
        filter,
        mapper
      );

  }


  public interface Capture< R > extends Iterable< Capture< R > > {

    Name getName ();

    R getValue ();

    Optional< Capture< R > > getPrevious ();

    default Capture< R > to (
      final Referent referent,
      final R value
    ) {

      return
        to (
          referent.reference (),
          value
        );

    }

    default Capture< R > to (
      final Reference reference,
      final R value
    ) {

      return
        to (
          reference.name (),
          value
        );

    }


    Capture< R > to (
      final Name name,
      final R value
    );

    Capture< R > to (
      final R value
    );


    /**
     * Returns a stream of captured change events starting with this capture.
     *
     * @return A stream of captures.
     */

    Stream< Capture< R > > stream ();

    /**
     * The total number of captures preceding this capture plus this capture.
     *
     * @return The number of captures in the linked list including this capture.
     */

    int size ();


  }


  public static < R > Capture< R > capture (
    final Referent referent,
    final R value
  ) {

    return
      capture (
        referent.reference (),
        value
      );

  }

  public static < R > Capture< R > capture (
    final Reference reference,
    final R value
  ) {

    return
      capture (
        reference.name (),
        value
      );

  }


  public static < R > Capture< R > capture (
    final Name name,
    final R value
  ) {

    return
      new CaptureRecord<> (
        name,
        value,
        null
      );

  }

  /**
   * Returns a new "initial" captured change event with the specified fields set.
   *
   * @param referent the referent
   * @param value    the value captured
   * @param previous the capture prior to this or null
   * @return A new capture with the previous property set to previous parameter value.
   */

  public static < R > Capture< R > capture (
    final Referent referent,
    final R value,
    final Capture< R > previous
  ) {

    return
      capture (
        referent.reference (),
        value,
        previous
      );

  }


  /**
   * Returns a new "initial" captured change event with the specified fields set.
   *
   * @param reference the reference
   * @param value     the value captured
   * @param previous  the capture prior to this or null
   * @return A new capture with the previous property set to previous parameter value.
   */

  public static < R > Capture< R > capture (
    final Reference reference,
    final R value,
    final Capture< R > previous
  ) {

    return
      capture (
        reference.name (),
        value,
        previous
      );

  }

  /**
   * Returns a new "initial" captured change event with the specified fields set.
   *
   * @param name     the name of the object the event relates to
   * @param value    the value captured
   * @param previous the capture prior to this or null
   * @return A new capture with the previous property set to previous parameter value.
   */

  public static < R > Capture< R > capture (
    final Name name,
    final R value,
    final Capture< R > previous
  ) {

    return
      previous == null ?
      capture (
        name,
        value
      ) :
      previous.to (
        name,
        value
      );

  }


  private static final class CaptureRecorder< E, R >
    implements Recorder< R > {

    private final Source< ? extends E >              source;
    private final Predicate< ? super E >             filter;
    private final Function< ? super E, ? extends R > mapper;

    private final Object     lock = new Object ();
    private       State< R > state;

    CaptureRecorder (
      final Source< ? extends E > source,
      final Predicate< ? super E > filter,
      final Function< ? super E, ? extends R > mapper
    ) {

      this.source =
        source;

      this.filter =
        filter;

      this.mapper =
        mapper;

    }


    @Override
    public void start () {

      synchronized ( lock ) {

        if ( isNull ( state ) ) {

          final var initial =
            new State< R > ();

          initial.subscription =
            source.consume (
              event -> {

                final E emittance =
                  event.emittance ();

                if ( filter.test ( emittance ) ) {
                  initial.records =
                    new CaptureRecord<> (
                      event.emitter ().name (),
                      mapper.apply (
                        emittance
                      ),
                      initial.records
                    );
                }

              }
            );

          state =
            initial;

        } else {

          throw
            new RuntimeException (
              "Recorder already started"
            );

        }

      }

    }

    @Override
    public Optional< Capture< R > > stop () {

      final Capture< R > records;

      synchronized ( lock ) {

        final var recording =
          state;

        if ( nonNull ( recording ) ) {

          recording
            .subscription
            .close ();

          records =
            recording.records;

          state =
            null;

        } else {

          throw
            new RuntimeException (
              "Recorder already stopped"
            );

        }

      }

      return
        ofNullable (
          records
        );

    }

    private static final class State< R > {

      private Subscription       subscription;
      private CaptureRecord< R > records;

      State () {}
    }

  }


  private static final class CaptureRecord< R > implements Capture< R > {

    private final Name               name;
    private final R                  value;
    final         CaptureRecord< R > previous;

    private final int hashCode;
    private final int index;

    CaptureRecord (
      final Name name,
      final R value,
      final CaptureRecord< R > previous
    ) {

      this.name =
        name;

      this.value =
        value;

      this.previous =
        previous;

      hashCode =
        Objects.hash (
          name,
          value,
          previous
        );

      index =
        nonNull ( previous )
        ? previous.index + 1
        : 0;

    }


    @Override
    public Name getName () {

      return
        name;

    }


    @Override
    public R getValue () {

      return
        value;

    }

    @Override
    public Optional< Capture< R > > getPrevious () {

      return
        ofNullable (
          previous
        );

    }

    @Override
    public Capture< R > to (
      final Name name,
      final R value
    ) {

      return
        new CaptureRecord<> (
          name,
          value,
          this
        );

    }

    @Override
    public Capture< R > to (
      final R value
    ) {

      return
        new CaptureRecord<> (
          name,
          value,
          this
        );

    }


    @Override
    public boolean equals (
      final Object o
    ) {

      if ( this == o )
        return
          true;

      if (

        o == null ||
          getClass () != o.getClass ()

      ) {

        return
          false;

      }

      //noinspection unchecked
      final var capture =
        (CaptureRecord< R >) o;

      return
        index == capture.index &&
          compare (
            this,
            capture,
            index
          );

    }

    private static < R > boolean compare (
      CaptureRecord< R > left,
      CaptureRecord< R > right,
      final int count
    ) {

      for (
        var i = count;
        i >= 0;
        i--
      ) {

        if (
          ( left.name == right.name ) &&
            Objects.equals ( left.value, right.value )
        ) {

          left =
            left.previous;

          right =
            right.previous;

        } else {

          return
            false;

        }

      }

      return
        true;

    }

    @Override
    public int size () {

      return
        index + 1;

    }

    @Override
    public int hashCode () {

      return
        hashCode;

    }

    @Override
    public String toString () {

      final var result =
        new StringBuilder ( 200 );

      var capture =
        this;

      do {

        result
          .append ( "CaptureRecord: { " )
          .append ( "name: " ).append ( capture.name ).append ( ", " )
          .append ( "value: " ).append ( value ).append ( ", " )
          .append ( "capture: " );

      } while (
        ( capture = capture.previous ) != null
      );

      result.append ( "null }" ).append ( "}".repeat ( max ( 0, index ) ) );

      return
        result.toString ();

    }

    @Override
    public Spliterator< Capture< R > > spliterator () {

      return
        Spliterators.spliterator (
          iterator (),
          size (),
          DISTINCT |
            NONNULL |
            IMMUTABLE
        );

    }


    @Override
    public Stream< Capture< R > > stream () {

      return
        StreamSupport.stream (
          spliterator (),
          false
        );

    }

    @Override
    public Iterator< Capture< R > > iterator () {

      //noinspection ReturnOfInnerClass
      return
        new Iterator<> () {

          CaptureRecord< R > current =
            CaptureRecord.this;

          @Override
          public boolean hasNext () {

            return
              current != null;

          }

          @Override
          public CaptureRecord< R > next () {

            final var result =
              current;

            if ( result != null ) {

              current =
                result.previous;

              return
                result;

            } else {

              throw
                new NoSuchElementException ();

            }

          }

        };

    }

  }

}
