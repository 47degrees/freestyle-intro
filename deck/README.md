
<!-- .slide: class="center" -->

## Getting into typed FP is hard because...

- No previous CT knowledge or math foundations <!-- .element: class="fragment" -->
- Leaving styles one is used to (i.e. OOP) <!-- .element: class="fragment" -->
- Rapid changing ecosystem <!-- .element: class="fragment" -->

---

## Freestyle's Goals

- Approachable to newcomers <!-- .element: class="fragment" -->
- Stack-safe <!-- .element: class="fragment" -->
- Dead simple integrations with Scala's library ecosystem <!-- .element: class="fragment" -->
- Help you build pure FP apps, libs & micro-services <!-- .element: class="fragment" -->

---

## In this talk

- <div> Freestyle programming style </div> <!-- .element: class="fragment" -->
- <div> **@free**, **@tagless**, **@service** </div> <!-- .element: class="fragment" -->
- <div> Effects </div><!-- .element: class="fragment" -->
- <div> RPC based Microservices </div><!-- .element: class="fragment" -->

---

## Interface + Impl driven design

```scala
@tagless(true)
trait ArithmeticService[F[_]] {
  def add(value1: Int, value2: Int): F[Int]
  def subtract(value1: Int, value2: Int): F[Int]
  def multiply(value1: Int, value2: Int): F[Int]
  def divide(value1: Int, value2: Int): F[Int]
}

class ArithmeticServiceHandler[F[_] : Monad] extends ArithmeticService[F] {
  override def add(value1: Int, value2: Int): F[Int] = (value1 + value2).pure[F]
  override def subtract(value1: Int, value2: Int): F[Int] = (value1 - value2).pure[F]
  override def multiply(value1: Int, value2: Int): F[Int] = (value1 * value2).pure[F]
  override def divide(value1: Int, value2: Int): F[Int] = (value1 / value2).pure[F]
}
```

---

## Freestyle's Workflow

- Declare your algebras <!-- .element: class="fragment" -->
- Compose your programs <!-- .element: class="fragment" -->
- Provide implicit implementations of each algebra Handler <!-- .element: class="fragment" -->
- Run your programs at the edge of the world <!-- .element: class="fragment" -->

---

## Freestyle's Workflow

Declare your algebras

```scala
@tagless(true)
trait ArithmeticService[F[_]] {
  def add(value1: Int, value2: Int): F[Int]
  def subtract(value1: Int, value2: Int): F[Int]
  def multiply(value1: Int, value2: Int): F[Int]
  def divide(value1: Int, value2: Int): F[Int]
}

@tagless(true)
trait ValidateService[F[_]] {
  def isPositive(value: Int): F[Boolean]
  def isZero(value: Int): F[Boolean]
}
```

---

## Freestyle's Workflow

Declare and compose programs

```scala
class ArithmeticProgram[F[_] : Async : ArithmeticService : ValidateService] {
  def program(value1: Int, value2: Int, value3: Int): F[Int] = for {
    result1 <- ArithmeticService[F].subtract(value1, value2)
    isZero <- ValidateService[F].isZero(result1)
    result2 <-
      if (isZero) Async[F].raiseError(new RuntimeException("Division by zero!"))
      else ArithmeticService[F].divide(value3, result1)
  } yield result2
}
```

---

## Freestyle's Workflow

Provide implicit evidence of your handlers to any desired target `M[_]`

```scala
class ArithmeticServiceHandler[F[_] : Async] extends ArithmeticService[F] {
  override def add(value1: Int, value2: Int): F[Int] = (value1 + value2).pure[F]
  override def subtract(value1: Int, value2: Int): F[Int] = (value1 - value2).pure[F]
  override def multiply(value1: Int, value2: Int): F[Int] = (value1 * value2).pure[F]
  override def divide(value1: Int, value2: Int): F[Int] = (value1 / value2).pure[F]
}

class ValidateServiceHandler[F[_] : Async] extends ValidateService[F] {
  override def isPositive(value: Int): F[Boolean] = (value > 0).pure[F]
  override def isZero(value: Int): F[Boolean] = (value == 0).pure[F]
}
```

---

## Freestyle's Workflow

Run your program to your desired target `M[_]`

```scala
object ArithmeticDemo extends App {

  implicit val arithmeticServiceForIO: ArithmeticServiceHandler[IO] = new ArithmeticServiceHandler[IO]
  implicit val validateServiceForIO: ValidateServiceHandler[IO] = new ValidateServiceHandler[IO]

  new ArithmeticProgram[IO].program(4, 2, 6).attempt.unsafeRunSync
  // res0: Either[Throwable, Int] = Right(3)
  new ArithmeticProgram[IO].program(3, 3, 10).attempt.unsafeRunSync
  // res1: Either[Throwable, Int] = Left(java.lang.RuntimeException: Division by zero!)
}
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

# RPC based Microservices

---

## What is RPC (Remote Procedure Call)

<div> Allow call methods in a remote app server as local </div><!-- .element: class="fragment" -->
<div> <img src="rpc.svg" style="margin-top: 1.5em ;width: 50%;"> </div><!-- .element: class="fragment" -->

---

## gRPC

- <div> open source high performance RPC framework </div> <!-- .element: class="fragment" -->
- <div> Idiomatic client libraries in several languages </div> <!-- .element: class="fragment" -->
- <div> Bi-directional streaming with http/2 based transport </div> <!-- .element: class="fragment" -->

---

## gRPC

Main usage scenarios

- <div> Connecting polyglot services in microservice-based architecture </div> <!-- .element: class="fragment" -->
- <div> Connecting mobile devices to backend services </div> <!-- .element: class="fragment" -->

---

## gRPC

Uses protocol buffers by default to:

- <div> **Define IDL**: service interfaces and structure of message</div> <!-- .element: class="fragment" -->
- <div> Serialize/deserialize structure data </div> <!-- .element: class="fragment" -->

---

## gRPC

Define your proto message

```protobuf
message Person {
  required string name = 1;
  required int32 id = 2;
  optional string email = 3;
}
```

---

## gRPC

Define your gRPC service

```protobuf
// The greeter service definition.
service Greeter {
  // Sends a greeting
  rpc SayHello (HelloRequest) returns (HelloReply);
}

// The request message containing the user's name.
message HelloRequest {
  string name = 1;
}

// The response message containing the greetings
message HelloReply {
  string message = 1;
}
```

---

## Freestyle RPC

Define your message
```scala
import freestyle._
import freestyle.rpc.protocol._

trait Messages {
  @message
  case class Person(name: String, id: Int, email: String)
}
```

---

## Freestyle RPC

Expose Algebras as RPC services

```scala
object protocol {

  /**
   * The request message containing the user's name.
   * @param name User's name.
   */
  @message
  case class HelloRequest(name: String)

  /**
   * The response message,
   * @param message Message containing the greetings.
   */
  @message
  case class HelloReply(message: String)

  @service
  trait Greeter[F[_]] {

    /**
     * The greeter service definition.
     *
     * @param request Say Hello Request.
     * @return HelloReply.
     */
    @rpc(Avro) def sayHello(request: HelloRequest): F[HelloReply]

  }
}
```

---

## Freestyle RPC gives you for free:

- <div> **gRPC Server**: gRPC based server. </div> <!-- .element: class="fragment" -->
- <div> **client** gRPC client. </div> <!-- .element: class="fragment" -->
- <div> **IDL** files to interoperate with other langs. </div> <!-- .element: class="fragment" -->

---

## Frestyle RPC
IDL generation

- <div> **idlGen**: sbt plugin</div> <!-- .element: class="fragment" -->

- <div> **Generation of IDL files** from Scala definition </div> <!-- .element: class="fragment" -->
- <div> **Generation of source files** from IDL (only supported for Avro) </div> <!-- .element: class="fragment" -->

---

## Frestyle RPC

Generation of IDL files from code

```scala
@option("java_multiple_files", true)
@option("java_outer_classname", "Quickstart")
@outputName("GreeterService")
@outputPackage("quickstart")
object service {

  @message
  case class HelloRequest(greeting: String)

  @message
  case class HelloResponse(reply: String)

  @service
  trait Greeter[F[_]] {

    @rpc(Protobuf)
    def sayHello(request: HelloRequest): F[HelloResponse]
     
    @rpc(Avro)
    def sayHelloAvro(request: HelloRequest): F[HelloResponse]

    @rpc(Protobuf)
    @stream[ResponseStreaming.type]
    def lotsOfReplies(request: HelloRequest): Observable[HelloResponse]
  }
  
}
```

```protobuf
syntax = "proto3";

option java_multiple_files = true;
option java_outer_class_name = "Quickstart";

package quickstart;

message HelloRequest {
  string greeting = 1;
}

message HelloResponse {
  string reply = 1;
}

service Greeter {
  rpc SayHello (HelloRequest) returns (HelloResponse);
  rpc LotsOfReplies (HelloRequest) returns (stream HelloResponse);
}
```

---

## Frestyle RPC

Generation of IDL files from code

```scala
sbt "idlGen proto"
```

```protobuf
syntax = "proto3";

option java_multiple_files = true;
option java_outer_class_name = "Quickstart";

package quickstart;

message HelloRequest {
  string greeting = 1;
}

message HelloResponse {
  string reply = 1;
}

service Greeter {
  rpc SayHello (HelloRequest) returns (HelloResponse);
  rpc LotsOfReplies (HelloRequest) returns (stream HelloResponse);
}
```

---

## Frestyle RPC

Generation of IDL files from code

```scala
sbt "idlGen avro"
```

```protobuf
{
  "namespace" : "quickstart",
  "protocol" : "GreeterService",
  "types" : [
    {
      "name" : "HelloRequest",
      "type" : "record",
      "fields" : [
        {
          "name" : "greeting",
          "type" : "string"
        }
      ]
    },
    {
      "name" : "HelloResponse",
      "type" : "record",
      "fields" : [
        {
          "name" : "reply",
          "type" : "string"
        }
      ]
    }
  ],
  "messages" : {
    "sayHelloAvro" : {
      "request" : [
        {
          "name" : "arg",
          "type" : "HelloRequest"
        }
      ],
      "response" : "HelloResponse"
    }
  }
}

```

---

## Frestyle RPC

Generation of source files from IDL

- <div> Only **Avro** is supported, in both **.avpr** (JSON) and **.avdl** (Avro IDL) formats</div> <!-- .element: class="fragment" -->

```scala 
sbt "srcGen avro"
```
<!-- .element: class="fragment" -->

```scala
sbt "srcGenFromJars"
```
<!-- .element: class="fragment" -->

---

## Frestyle RPC

Patterns: Server

```scala
class GreeterServiceHandler[F[_] : Async](implicit S: Scheduler) extends GreeterService[F] {
  override def sayHello(request: HelloRequest): F[HelloReply] = HelloReply(s"Hi ${request.name}!").pure[F]

  override def lotsOfReplies(request: HelloRequest): Observable[HelloReply] =
    Observable.fromIterable(1 to 5).map(index => HelloReply(s"Message number $index for ${request.name}"))

  override def lotsOfGreetings(requests: Observable[HelloRequest]): F[HelloReply] =
    requests.foldLeftL(List.empty[String]) {
      case (names, request) =>
        names :+ request.name
    }
      .map(names => HelloReply(s"Hello to ${names.mkString(",")}!"))
      .to[F]

  override def lotsOfMessages(requests: Observable[HelloRequest]): Observable[HelloReply] =
    requests.flatMap(request =>
      Observable.fromIterable(1 to 5).map(index => HelloReply(s"Message number $index for ${request.name}"))
    )
}
```

---

## Frestyle RPC

Patterns: Server

```scala
trait CommonRuntime {

  implicit val S: Scheduler = monix.execution.Scheduler.Implicits.global

}

object gserver {

  trait Implicits extends CommonRuntime {
    implicit val greeterServiceHandler: GreeterServiceHandler[IO] = new GreeterServiceHandler[IO]

    val grpcConfigs: List[GrpcConfig] = List(
      AddService(GreeterService.bindService[IO])
    )

    implicit val serverW: ServerW = ServerW.default(8080, grpcConfigs)
  }

  object implicits extends Implicits
}
```

---

## Frestyle RPC

Patterns: Running the server

```scala
object ServerApp extends App {

  import gserver.implicits._

  server[IO].unsafeRunSync()
}
```

---

## Frestyle RPC

Patterns: Client

```scala
object gclient {

  trait Implicits extends CommonRuntime {
    val channelFor: ChannelFor = ChannelForAddress("localhost", 8080)

    implicit val greeterServiceClient: GreeterService.Client[Task] = GreeterService.client[Task](channelFor)
  }

  object implicits extends Implicits
}
```

---

## Frestyle RPC

Patterns: Running the client

```scala
object ClientApp extends App {

  import gclient.implicits._

  println(Await.result(greeterServiceClient.sayHello(HelloRequest("John")).runAsync, Duration.Inf))
}
```

---

## Frestyle RPC

Other supported features

- <div> **SSL/TLS encryption** </div> <!-- .element: class="fragment" -->
- <div> **Metrics reporting** </div> <!-- .element: class="fragment" -->

---

### Inspired by ###

- [**Cats**](http://typelevel.org/cats/)
- [**Scalaz**](https://github.com/scalaz/scalaz)
- [**ΛRROW**](http://arrow-kt.io/)
- [**Eff**](http://atnos-org.github.io/eff/)
- [**Fetch**](http://47deg.github.io/fetch/)
- [**Simulacrum**](https://github.com/mpilquist/simulacrum)

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
... and many more contributors
```

---

### Thanks! ###

- [http://frees.io](http://frees.io)
- [https://github.com/frees-io/freestyle-rpc/tree/master/modules/examples](https://github.com/frees-io/freestyle-rpc/tree/master/modules/examples)


