package org.rapidpm.frp.v002;

import static java.util.concurrent.CompletableFuture.supplyAsync;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.rapidpm.frp.Transformations;
import org.rapidpm.frp.functions.TriFunction;
import org.rapidpm.frp.model.Pair;

/**
 *
 */
public class Main {


  public static Function<String, CompletableFuture<String>> transformA() {
    return (input) -> CompletableFuture.supplyAsync(() -> input);
  }


  private static final Function<String, String> step1 = (input) -> input.toUpperCase();
  private static final Function<String, String> step2 = (input) -> input + " next A";
  private static final Function<String, String> step3 = (input) -> input + " next B";

  public static void main(String[] args) {
    final String hello = step1
        .andThen(step2)
        .andThen(step3)
        .apply("hello"); // blocking call


    // target definition
    // Function<String, CompletableFuture<String>> finalFunction = start.combine(f1).combine(f2).combine(f3);
    //CompletableFuture<String> result = finalFunction.apply(value);


    TriFunction<
        Function<String, String>,
        Function<String, String>,
        Function<String, String>,
        Function<String, CompletableFuture<String>>> inputTriA = (f1 , f2 , f3) -> {

      return (value) -> {
        final CompletableFuture<String> result1 = supplyAsync(() -> f1.apply(value));

        final CompletableFuture<String> result2 = result1.thenComposeAsync(v -> supplyAsync(() -> f2.apply(v)));

        final CompletableFuture<String> result3 = result2.thenComposeAsync(v -> supplyAsync(() -> f3.apply(v)));

        return result3;
      };
    };


    //
    TriFunction<String, String, String, Integer> triDemo = (s1 , s2 , s3) -> {return - 1;};
    final Function<String, Function<String, Function<String, Integer>>> triDemoCurried
        = Transformations.<String, String, String, Integer>curryTriFunction().apply(triDemo);
    final Integer i = triDemoCurried.apply("A").apply("B").apply("C");


    final Function<Function<String, String>, Function<Function<String, String>, Function<Function<String, String>, Function<String, CompletableFuture<String>>>>> apply
        = Transformations.<Function<String, String>, Function<String, String>, Function<String, String>, Function<String, CompletableFuture<String>>>curryTriFunction().apply(inputTriA);

    final Function<String, CompletableFuture<String>> resultCF = apply.apply(step1).apply(step2).apply(step3);
    final CompletableFuture<String> cf = resultCF.apply("hello World");
    cf.thenAcceptAsync(System.out::println);


    //##################

    TriFunction<
        Function<String, String>,
        Function<String, String>,
        Function<String, String>,
        Function<String, CompletableFuture<String>>> inputTriB
        = (f1 , f2 , f3)
        -> (value)
        ->
        CompletableFuture
            .completedFuture(value)
            .thenComposeAsync(v -> supplyAsync(() -> f1.apply(v)))
            .thenComposeAsync(v -> supplyAsync(() -> f2.apply(v)))
            .thenComposeAsync(v -> supplyAsync(() -> f3.apply(v)));


    //manual
    Function<String, CompletableFuture<String>> f = (value) ->
        CompletableFuture
            .completedFuture(value)
            .thenComposeAsync(v -> supplyAsync(() -> step1.apply(v)))
            .thenComposeAsync(v -> supplyAsync(() -> step2.apply(v)))
            .thenComposeAsync(v -> supplyAsync(() -> step3.apply(v)));

    System.out.println("f.apply(\"hello\") = " + f.apply("hello").join());

    final Function<String, Integer> step1A = Integer::parseInt;
    final Function<Integer, String> step2A = (input) -> input + " next A";
    final Function<String, Pair<String, Integer>> step3A = (input) -> new Pair<>(input , input.length());

    Function<String, CompletableFuture<Pair<String, Integer>>> fB = (value) ->
        CompletableFuture
            .completedFuture(value)
            .thenComposeAsync(v -> supplyAsync(() -> step1A.apply(v)))
            .thenComposeAsync(v -> supplyAsync(() -> step2A.apply(v)))
            .thenComposeAsync(v -> supplyAsync(() -> step3A.apply(v)));



    final Function<String, CompletableFuture<Pair<String, Integer>>> fA = CFQ
        .define(step1A)
        .thenCombineAsync(step2A)
        .thenCombineAsync(step3A)
        .resultFunction();

    final CompletableFuture<Pair<String, Integer>> cfA = fA
        .apply("hello");

    final String hello1 = cfA
        .join()
        .getT1();

  }


  public static <A, B, C, D> TriFunction<
      Function<A, B>,
      Function<B, C>,
      Function<C, D>,
      Function<A, CompletableFuture<D>>> genericTriFunction() {

    return (f1 , f2 , f3)
        -> (value)
        ->
        CompletableFuture
            .completedFuture(value)
            .thenComposeAsync(v -> supplyAsync(() -> f1.apply(v)))
            .thenComposeAsync(v -> supplyAsync(() -> f2.apply(v)))
            .thenComposeAsync(v -> supplyAsync(() -> f3.apply(v)));
  }

  // Beispiel TriFunction mit Generics
  // Image that show type switching a->b , b->c , c->d  => f(a->d)

  public static class CFQ<T, R> {

    private Function<T, CompletableFuture<R>> resultFunction;

    private CFQ(Function<T, CompletableFuture<R>> resultFunction) {
      this.resultFunction = resultFunction;
    }

    public static <T, R> CFQ<T, R> define(Function<T, R> transformation) {
      return new CFQ<>(t -> CompletableFuture.completedFuture(transformation.apply(t)));
    }

    public <N> CFQ<T, N> thenCombineAsync(Function<R, N> nextTransformation) {
      final Function<T, CompletableFuture<N>> f = this.resultFunction
          .andThen(before -> before.thenComposeAsync(v -> supplyAsync(() -> nextTransformation.apply(v))));
      return new CFQ<>(f);
    }

    public Function<T, CompletableFuture<R>> resultFunction() {
      return this.resultFunction;
    }
  }
}
