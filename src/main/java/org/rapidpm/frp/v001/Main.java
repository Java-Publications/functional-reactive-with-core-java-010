package org.rapidpm.frp.v001;

import static java.lang.String.valueOf;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.supplyAsync;

import java.nio.charset.Charset;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.rapidpm.frp.functions.CheckedFunction;
import org.rapidpm.frp.model.Pair;
import org.rapidpm.frp.model.Result;

/**
 *
 */
public class Main {

  private static final int nThreads = Runtime.getRuntime()
                                             .availableProcessors();


  private static final ExecutorService poolToWait = Executors
      .newFixedThreadPool(nThreads * 10);

  private static final ExecutorService poolToWork = Executors
      .newFixedThreadPool(nThreads);


  public static Function<String, Result<Integer>> parseIntChecked() {
    return (CheckedFunction<String, Integer>) Integer::parseInt;
  }

  public static void main(String[] args) {


    Consumer<CompletableFuture<Result<?>>> print = (cf) -> cf
        .whenCompleteAsync((result , throwable) -> {
          if (throwable == null)
            result.ifPresentOrElse(
                System.out::println ,
                (Consumer<String>) System.out::println
            );
          else System.out.println("throwable = " + throwable);
        })
        .join();


    final Supplier<String> nextStringValue = () -> valueOf(new Random().nextInt(10));


//    final CompletableFuture<Integer> step02 = supplyAsync(nextStringValue)
//        .thenComposeAsync(new Function<String, CompletionStage<Integer>>() {
//          @Override
//          public CompletionStage<Integer> apply(String s) {
//            return CompletableFuture.completedFuture(Integer.parseInt(s));
//          }
//        });

    final CompletableFuture<String> step02 = supplyAsync(nextStringValue)
        .thenComposeAsync(s -> completedFuture(Integer.parseInt(s)))
        .handleAsync(new BiFunction<Integer, Throwable, String>() {
          @Override
          public String apply(Integer value , Throwable throwable) {
            return (throwable == null)
                   ? "all is ok, value is " + value
                   : throwable.getMessage();
          }
        });
//    final CompletableFuture<String> step02 = supplyAsync(nextStringValue)
//        .thenComposeAsync(s -> completedFuture(Integer.parseInt(s)))
//        .handleAsync((value , throwable) -> (throwable == null)
//                                            ? "all is ok, value is " + value
//                                            : throwable.getMessage());


    //to switch into Result World
    final CompletableFuture<Result<Integer>> handleAsyncA = supplyAsync(nextStringValue , poolToWork)
        .handleAsync((value , throwable) -> (throwable == null)
                                            ? Result.success(Integer.parseInt(value))
                                            : Result.failure(throwable.getMessage()));

    //wrapping into Result only
    final CompletableFuture<Result<String>> handleAsyncB = supplyAsync(nextStringValue , poolToWork)
        .handleAsync((value , throwable) -> (throwable == null)
                                            ? Result.success(value)
                                            : Result.failure(throwable.getMessage()));


//    final CompletableFuture<Result<Integer>> composeAsyncA = supplyAsync(nextStringValue , poolToWork)
//        .thenComposeAsync(new Function<String, CompletionStage<Result<Integer>>>() {
//          @Override
//          public CompletionStage<Result<Integer>> apply(String s) {
////            final int value = Integer.parseInt(s); // could throw Exception
//            CheckedFunction<String, Integer> f = Integer::parseInt;
//            final Result<Integer> result = f.apply(s);
//            return completedFuture(result);
//          }
//        });

    final CompletableFuture<Result<Integer>> composeAsyncA = supplyAsync(nextStringValue , poolToWork)
        .thenComposeAsync(s -> {
          CheckedFunction<String, Integer> f = Integer::parseInt;
          final Result<Integer> result = f.apply(s);
          return completedFuture(result);
        });

    final CompletableFuture<Result<Integer>> composeAsyncB = supplyAsync(nextStringValue , poolToWork)
        .thenComposeAsync(s -> completedFuture(parseIntChecked().apply(s)));

    final CompletableFuture<Result<Integer>> composeAsyncC = supplyAsync(nextStringValue , poolToWork)
        .thenComposeAsync(s -> completedFuture(((CheckedFunction<String, Integer>) Integer::parseInt).apply(s)));


    // chain steps

    final CompletableFuture<Result<String>> resultA = composeAsyncC
        .thenComposeAsync(input -> completedFuture(input.isPresent()
                                                   ? input.map(integer -> "Result is now " + integer)
                                                   : Result.<String>failure("Value was not available")));

    //together
    final CompletableFuture<Result<String>> resultB = supplyAsync(nextStringValue , poolToWork)
        .thenComposeAsync(input -> completedFuture(((CheckedFunction<String, Integer>) Integer::parseInt).apply(input)))
        .thenComposeAsync(input -> completedFuture(input.isPresent()
                                                   ? input.map(integer -> "Result is now " + integer)
                                                   : Result.<String>failure("Value was not available")));


    final BiFunction<Result<Integer>, Function<Integer, Result<String>>, Result<String>> flatMap
        = (input , funct) -> (input.isPresent())
                             ? funct.apply(input.get())
                             : input.asFailure();

    final Result<String> stringResultA = flatMap.apply(Result.success(1) ,
                                                       ((CheckedFunction<Integer, String>) i -> "Result is now " + i));
    final Result<String> stringResultB = flatMap.apply(Result.failure("ohoh") ,
                                                       ((CheckedFunction<Integer, String>) i -> "Result is now " + i));


    supplyAsync(nextStringValue , poolToWork)
        .thenComposeAsync(input -> completedFuture(((CheckedFunction<String, Integer>) Integer::parseInt).apply(input)))
        .thenComposeAsync(input -> completedFuture(flatMap.apply(input , new Function<Integer, Result<String>>() {
          @Override
          public Result<String> apply(Integer integer) {
            return Result.success("Result is now " + integer);
          }
        })));

    supplyAsync(nextStringValue , poolToWork)
        .thenComposeAsync(input -> completedFuture(((CheckedFunction<String, Integer>) Integer::parseInt).apply(input)))
        .thenComposeAsync(input -> completedFuture(flatMap.apply(input , integer -> Result.success("Result is now " + input))));

    supplyAsync(nextStringValue , poolToWork)
        .thenComposeAsync(input -> completedFuture(((CheckedFunction<String, Integer>) Integer::parseInt).apply(input)))
        .thenComposeAsync(input -> completedFuture(input.flatMap(new Function<Integer, Result<String>>() {
          @Override
          public Result<String> apply(Integer integer) {
            return Result.success("Result is now " + integer);
          }
        })));

    supplyAsync(nextStringValue , poolToWork)
        .thenComposeAsync(input -> completedFuture(((CheckedFunction<String, Integer>) Integer::parseInt).apply(input)))
        .thenComposeAsync(input -> completedFuture(input.flatMap(integer -> Result.success("Result is now " + integer))));

    //now exception catching
    supplyAsync(nextStringValue , poolToWork)
        .thenComposeAsync(input -> completedFuture(((CheckedFunction<String, Integer>) Integer::parseInt).apply(input)))
        .thenComposeAsync(input -> completedFuture(input.flatMap(new CheckedFunction<Integer, String>() {
          @Override
          public String applyWithException(Integer integer) throws Exception {
            return "Result is now " + integer;
          }
        })));

    print.accept(
        supplyAsync(nextStringValue , poolToWork)
            .thenComposeAsync(input -> completedFuture(((CheckedFunction<String, Integer>) Integer::parseInt).apply(input)))
            .thenComposeAsync(input -> completedFuture(input.flatMap((CheckedFunction<Integer, String>) integer -> "Result is now " + integer)))
    );


    // skip functions time only - not the pool time
    print.accept(
        supplyAsync(() -> "oh no" , poolToWork)
            .thenComposeAsync(input -> completedFuture(((CheckedFunction<String, Integer>) Integer::parseInt).apply(input)))
            .thenComposeAsync(input -> completedFuture(input.flatMap((CheckedFunction<Integer, String>) integer -> "Result is now " + integer)))
    );

    //TODO - show time consumption
    //

    print.accept(
        supplyAsync(() -> "oh no" , poolToWork)
            .thenComposeAsync(input -> completedFuture(((CheckedFunction<String, Integer>) Integer::parseInt).apply(input)))
            .thenComposeAsync(input -> completedFuture(input.flatMap((CheckedFunction<Integer, String>) integer -> "Result is now " + integer)))
    );


    //DEMO to consume time

    Supplier<String> dataRaw = () -> {
      byte[] array = new byte[1024 * 1024 * 200];
      new Random(System.nanoTime()).nextBytes(array);
      return new String(array , Charset.forName("UTF-8"));
    };

    //stolen from http://www.rationaljava.com/2015/06/java8-generate-random-string-in-one-line.html
    Function<Pair<Random, Integer>, String> randomString = (p) -> p.getT1()
                                                                   .ints(48 , 122)
                                                                   .filter(i -> (i < 57 || i > 65) && (i < 90 || i > 97))
                                                                   .mapToObj(i -> (char) i)
                                                                   .limit(p.getT2())
                                                                   .collect(StringBuilder::new , StringBuilder::append , StringBuilder::append)
                                                                   .toString();


    final CompletableFuture<Void> cfVoid = supplyAsync(() -> "oh no" , poolToWork)
        .thenAcceptBothAsync(supplyAsync(() -> "oh no again" , poolToWork) , (s1 , s2) -> System.out.println("s = " + s1 + s2));


    // concurrent cal from both input values
    final CompletableFuture<String> cf = supplyAsync(() -> "oh no" , poolToWork)
        .thenCombineAsync(supplyAsync(() -> "oh no again" , poolToWork) ,
                          (s1 , s2) -> "done");


    // parallel Strings compress via CompletableFuture


    poolToWait.shutdown();
    poolToWork.shutdown();
  }

}
