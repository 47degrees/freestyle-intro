
<!-- .slide: class="center" -->

## Getting into typed FP is hard because...

- No previous CT knowledge or math foundations <!-- .element: class="fragment" -->
- Leaving styles one is used to (ex. OOP) <!-- .element: class="fragment" -->
- Lack of docs on how to properly use Monad Transformers and other techniques required to do concise FP. <!-- .element: class="fragment" -->
- Rapid changing ecosystem <!-- .element: class="fragment" -->
- Scala has not been designed to support first class typeclasses, sum types, etc. <!-- .element: class="fragment" -->
- Proliferation of IO-like types <!-- .element: class="fragment" -->
    - scala.concurrent.Future
    - fs2.Task
    - monix.eval.Task
    - cats.effects.IO

---

## Freestyle's Goals

- Approachable to newcomers <!-- .element: class="fragment" -->
- Stack-safe <!-- .element: class="fragment" -->
- Dead simple integrations with Scala's library ecosystem <!-- .element: class="fragment" -->

---

## In this talk

- <div> Freestyle programming style </div> <!-- .element: class="fragment" -->
- <div> **@free**, **@tagless**, **@modules** </div> <!-- .element: class="fragment" -->
- <div> Effects </div><!-- .element: class="fragment" -->
- <div> Integrations </div><!-- .element: class="fragment" -->
- <div> Optimizations (iota Coproduct, stack-safe @tagless) </div> <!-- .element: class="fragment" -->
- <div> What's next <!-- .element: class="fragment" --> </div>

---

## Interface + Impl driven design

```scala
@free trait Interact {
  def ask(prompt: String): FS[String]
  def tell(msg: String): FS[Unit]
}

implicit val handler: Interact.Handler[Future] = new Interact.Handler[Future] {
  def ask(prompt: String): Future[String] = ???
  def tell(msg: String): Future[Unit] = ???
}
```

---

## Boilerplate Reduction > Declaration

```diff
+ @free trait Interact {
+  def ask(prompt: String): FS[String]
+  def tell(msg: String): FS[Unit]
+ }
- sealed trait Interact[A]
- case class Ask(prompt: String) extends Interact[String]
- case class Tell(msg: String) extends Interact[Unit]
-
- class Interacts[F[_]](implicit I: InjectK[Interact, F]) {
-  def tell(msg: String): Free[F, Unit] = Free.inject[Interact, F](Tell(msg))
-  def ask(prompt: String): Free[F, String] = Free.inject[Interact, F](Ask(prompt))
- }
-
- object Interacts {
-  implicit def interacts[F[_]](implicit I: InjectK[Interact, F]): Interacts[F] = new Interacts[F]
-  def apply[F[_]](implicit ev: Interacts[F]): Interacts[F] = ev
- }
```

---

## Boilerplate Reduction > Composition

```diff
+ @module trait App {
+  val exerciseOp: ExerciseOp
+  val userOp: UserOp
+  val userProgressOp: UserProgressOp
+  val githubOp: GithubOp
+ }
- type C01[A] = Coproduct[ExerciseOp, UserOp, A]
- type C02[A] = Coproduct[UserProgressOp, C01, A]
- type ExercisesApp[A] = Coproduct[GithubOp, C02, A]
- val exerciseAndUserInterpreter: C01 ~> M = exerciseOpsInterpreter or userOpsInterpreter
- val userAndUserProgressInterpreter: C02 ~> M = userProgressOpsInterpreter or exerciseAndUserInterpreter
- val allInterpreters: ExercisesApp ~> M = githubOpsInterpreter or userAndUserProgressInterpreter
```

---

## Freestyle's Workflow

- Declare your algebras <!-- .element: class="fragment" -->
- Group them into modules <!-- .element: class="fragment" -->
- Compose your programs <!-- .element: class="fragment" -->
- Provide implicit implementations of each algebra Handler <!-- .element: class="fragment" -->
- Run your programs at the edge of the world <!-- .element: class="fragment" -->

---

## Freestyle's Workflow

Declare your algebras

```tut:silent
import freestyle._
import freestyle.tagless._

object algebras {
  /* Handles user interaction */
  @free trait Interact {
    def ask(prompt: String): FS[String]
    def tell(msg: String): FS[Unit]
  }

  /* Validates user input */
  @tagless trait Validation {
    def minSize(s: String, n: Int): FS[Boolean]
    def hasNumber(s: String): FS[Boolean]
  }
}
```

---

## Freestyle's Workflow

Combine your algebras in arbitrarily nested modules

```tut:silent
import algebras._
import freestyle.effects.error._
import freestyle.effects.error.implicits._
import freestyle.effects.state
val st = state[List[String]]
import st.implicits._

object modules {

  @module trait App {
    val validation: Validation.StackSafe
    val interact: Interact
    val errorM : ErrorM
    val persistence: st.StateM
  }

}
```

---

## Freestyle's Workflow

Declare and compose programs

```tut:silent
import cats.implicits._

def program[F[_]]
  (implicit I: Interact[F], R: st.StateM[F], E: ErrorM[F], V: Validation.StackSafe[F]): FreeS[F, Unit] = {
  for {
    cat <- I.ask("What's the kitty's name?")
    isValid <- (V.minSize(cat, 5), V.hasNumber(cat)).mapN(_ && _) //may run ops in parallel
    _ <- if (isValid) R.modify(cat :: _) else E.error(new RuntimeException("invalid name!"))
    cats <- R.get
    _ <- I.tell(cats.toString)
  } yield ()
}
```

---

## Freestyle's Workflow

Provide implicit evidence of your handlers to any desired target `M[_]`

```tut:silent
import cats.effect.IO
import cats.effect.implicits._
import cats.data.StateT

type Target[A] = StateT[IO, List[String], A]

implicit val interactHandler: Interact.Handler[Target] = new Interact.Handler[Target] {
  def ask(prompt: String): Target[String] = tell(prompt) >> StateT.lift("Isidoro1".pure[IO])
  def tell(msg: String): Target[Unit] = StateT.lift(IO { println(msg) })
}

implicit val validationHandler: Validation.Handler[Target] = new Validation.Handler[Target] {
  def minSize(s: String, n: Int): Target[Boolean] = StateT.lift((s.length >= n).pure[IO])
  def hasNumber(s: String): Target[Boolean] = StateT.lift(s.exists(c => "0123456789".contains(c)).pure[IO])
}
```

---

## Freestyle's Workflow

Run your program to your desired target `M[_]`

```tut:book
import modules._
import freestyle.implicits._
import cats.mtl.implicits._

val concreteProgram = program[App.Op]
concreteProgram.interpret[Target].runEmpty.unsafeRunSync
```

---

## Effects

Error

```tut:book
import freestyle.effects.error._
import freestyle.effects.implicits._

type EitherTarget[A] = Either[Throwable, A]

def shortCircuit[F[_]: ErrorM] =
  for {
    a <- FreeS.pure(1)
    b <- ErrorM[F].error[Int](new RuntimeException("BOOM"))
    c <- FreeS.pure(1)
  } yield a + b + c

shortCircuit[ErrorM.Op].interpret[EitherTarget]

shortCircuit[ErrorM.Op].interpret[IO].attempt.unsafeRunSync
```

---

## Effects

Option

```tut:book
import freestyle.effects.option._
def programNone[F[_]: OptionM] =
  for {
    a <- FreeS.pure(1)
    b <- OptionM[F].option[Int](None)
    c <- FreeS.pure(1)
  } yield a + b + c

programNone[OptionM.Op].interpret[Option]

programNone[OptionM.Op].interpret[List]
```

---

## Effects

Validation

```tut:silent
import freestyle.effects.validation
import cats.data.State

sealed trait ValidationError
case class NotValid(explanation: String) extends ValidationError

val v = validation[ValidationError]
import v.implicits._

type ValidationResult[A] = State[List[ValidationError], A]

def programErrors[F[_]: v.ValidationM] =
  for {
    _ <- v.ValidationM[F].invalid(NotValid("oh no"))
    errs <- v.ValidationM[F].errors
    _ <- v.ValidationM[F].invalid(NotValid("this won't be in errs"))
  } yield errs

programErrors[v.ValidationM.Op].interpret[ValidationResult].runEmpty.value
// res11: (List[ValidationError], List[ValidationError]) = (List(NotValid(oh no), NotValid(this won't be in errs)),List(NotValid(oh no)))
```

---

## Effects

An alternative to monad transformers

- <div> **error**: Signal errors </div> <!-- .element: class="fragment" -->
- <div> **either**: Flattens if `Right` / short-circuit `Left` </div> <!-- .element: class="fragment" -->
- <div> **option**: Flatten `Some` / short-circuit on `None` </div> <!-- .element: class="fragment" -->
- <div> **reader**: Deffer dependency injection until program interpretation </div> <!-- .element: class="fragment" -->
- <div> **writer**: Log / Accumulate values </div> <!-- .element: class="fragment" -->
- <div> **state**: Pure functional state threaded over the program monadic sequence </div> <!-- .element: class="fragment" -->
- <div> **traverse**: Generators over `Foldable` </div> <!-- .element: class="fragment" -->
- <div> **validation**: Accumulate and inspect errors throughout the monadic sequence </div> <!-- .element: class="fragment" -->
- <div> **async**: Integrate with callback based API's </div> <!-- .element: class="fragment" -->

---

## Integrations

- <div> **Monix**: Target runtime and `async` effect integration. </div> <!-- .element: class="fragment" -->
- <div> **Fetch**: Algebra to run fetch instances + Auto syntax `Fetch -> FS`. </div> <!-- .element: class="fragment" -->
- <div> **FS2**: Embed FS2 `Stream` in Freestyle programs. </div> <!-- .element: class="fragment" -->
- <div> **Doobie**: Embed `ConnectionIO` programs into Freestyle. </div> <!-- .element: class="fragment" -->
- <div> **Slick**: Embed `DBIO` programs into Freestyle. </div> <!-- .element: class="fragment" -->
- <div> **Akka Http**: `EntityMarshaller`s to return Freestyle programs in Akka-Http endpoints. </div> <!-- .element: class="fragment" -->
- <div> **Play**: Implicit conversions to return Freestyle programs in Play Actions. </div> <!-- .element: class="fragment" -->
- <div> **Twitter Util**: `Capture` instances for Twitter's `Future` & `Try`. </div> <!-- .element: class="fragment" -->
- <div> **Finch**: Mapper instances to return Freestyle programs in Finch endpoints. </div> <!-- .element: class="fragment" -->
- <div> **Http4s**: `EntityEncoder` instance to return Freestyle programs in Http4S endpoints. </div> <!-- .element: class="fragment" -->

---

## Optimizations

Freestyle provides optimizations for Free + Inject + Coproduct compositions as in
[DataTypes a la Carte](http://www.cs.ru.nl/~W.Swierstra/Publications/DataTypesALaCarte.pdf)

---

## Optimizations

[A fast Coproduct type based on Iota](https://github.com/47deg/iota) with constant evaluation time based on
 `@scala.annotation.switch` on the Coproduct's internal indexed values.

```tut:book
import iota._
import iota.debug.options.ShowTrees

val interpreter: FSHandler[App.Op, Target] = CopK.FunctionK.summon
```

---

## Optimizations

Freestyle does not suffer from degrading performance as the number of Algebras increases in contrast
with `cats.data.EitherK`

<canvas id="bench-coproduct"></canvas>

<script>
renderCoproductGraph();
</script>

---

## Optimizations

Optimizations over the pattern matching of `FunctionK` for user defined algebras to translate them
into a JVM switch with `@scala.annotation.switch`.

<canvas id="bench-functionk"></canvas>

<script>
$( document ).ready(function() { renderFunctionKGraph(); });
</script>

---

## Optimizations

Brings ADT-less stack safety to `@tagless` Algebras
without rewriting interpreters to `Free[M, ?]` where `M[_]` is stack unsafe.

```scala
program[Option] // Stack-unsafe
program[StackSafe[Option]#F] // lift handlers automatically to Free[Option, ?] without the `@free` ADTs overhead
```

---

## What's next?

- More integrations <!-- .element: class="fragment" -->
- More syntax and runtime optimizations <!-- .element: class="fragment" -->
- IntelliJ IDEA support (scala meta) <!-- .element: class="fragment" -->
- Akka actors integration <!-- .element: class="fragment" -->
- Kafka client library <!-- .element: class="fragment" -->
- Cassandra client library <!-- .element: class="fragment" -->
- Microservice / RPC modules (Derive typesafe client and endpoints based on Protocol definitions) <!-- .element: class="fragment" -->

---

### Inspired by ###

- <[Cats](http://typelevel.org/cats/)>
- <[Simulacrum](https://github.com/mpilquist/simulacrum)> 

---

### Brought to you by... #

```
[colin-passiv](https://github.com/colin-passiv)
Adrián Ramírez Fornell <[AdrianRaFo](https://github.com/AdrianRaFo)>
Alejandro Gómez <[dialelo](https://github.com/dialelo)>
Ana Mª Marquez <[anamariamv](https://github.com/anamariamv)>
Andy Scott <[andyscott](https://github.com/andyscott)>
Diego Esteban Alonso Blas <[diesalbla](https://github.com/diesalbla)>
Domingo Valera <[dominv](https://github.com/dominv)>
Fede Fernández <[fedefernandez](https://github.com/fedefernandez)>
Francisco Diaz <[franciscodr](https://github.com/franciscodr)>
Giovanni Ruggiero <[gruggiero](https://github.com/gruggiero)>
Javi Pacheco <[javipacheco](https://github.com/javipacheco)>
Javier de Silóniz Sandino <[jdesiloniz](https://github.com/jdesiloniz)>
Jisoo Park <[guersam](https://github.com/guersam)>
Jorge Galindo <[jorgegalindocruces](https://github.com/jorgegalindocruces)>
Juan Pedro Moreno <[juanpedromoreno](https://github.com/juanpedromoreno)>
Juan Ramón González <[jrgonzalezg](https://github.com/jrgonzalezg)>
Maureen Elsberry  <[MaureenElsberry](https://github.com/MaureenElsberry)>
Peter Neyens <[peterneyens](https://github.com/peterneyens)>
Raúl Raja Martínez <[raulraja](https://github.com/raulraja)>
Sam Halliday <[fommil](https://github.com/fommil)>
Suhas Gaddam <[suhasgaddam](https://github.com/suhasgaddam)>
```

---

### Thanks! ###

http://frees.io
@raulraja @47deg
