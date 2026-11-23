<div dir="rtl">

# گزارش بازبینی کد (`Code Review`): بررسی `Design Pattern`ها و `Bad Smell`ها در شبیه‌ساز آسانسور (`CA3`)

**درس:** آزمون نرم‌افزار — تمرین سوم  
**استاد:** دکتر احسان خامس‌پناه

| عضو گروه | نام و نام خانوادگی  | شماره دانشجویی |
|---|---------------------|----------------|
| نفر اول | سید علیرضا میرشفیعی | 810101532      |
| نفر دوم | علیرضا کریمی        | 810101492      |

## ۱. مقدمه و مبناهای ارزیابی

در این گزارش، کدهای عملیاتی `src/main` و آزمون‌های `src/test` بررسی شده‌اند. مبنای بررسی، `Design for Testability` در اسلاید ۱۲ با تمرکز بر `Observability`، `Controllability` و `Decomposability`، و همچنین شناسایی `Code Smell`ها و `Anti-Pattern`ها در اسلاید ۱۳ برای بهبود خوانایی و نگهداری آزمون‌ها است.

## ۲. `Design Pattern`ها و نقاط قوت آزمون‌پذیری

### ۲.۱. پکیج `config` و فایل `elevator.properties`

#### P1: <span dir="ltr">`Configuration by Contract`</span>

**کلاس‌های درگیر:** `ElevatorConfiguration` و `ElevatorFactory`  
**محل:** متدهای `ElevatorConfiguration.load()` و `ElevatorFactory.createService()`.

</div>

<div dir="ltr">

```java
Properties config = new ElevatorConfiguration().load();
int initialFloor = Integer.parseInt(config.getProperty("initial.floor", "1"));
Elevator elevator = new Elevator(initialFloor, 0, 10);
Motor motor;
if ("legacy".equalsIgnoreCase(config.getProperty("motor.type", "legacy")))
    motor = new LegacyMotorAdapter(new LegacyLiftMotor());
else
    motor = new RecordingMotor();
```

</div>

<div dir="rtl">

**توضیح:** مقادیر `initial.floor` و `motor.type` از فایل `elevator.properties` خوانده می‌شوند. تغییر این تنظیمات بدون تغییر منطق سرویس، انعطاف‌پذیری و `Controllability` را افزایش می‌دهد.

#### P2: <span dir="ltr">`Inversion of Control`</span> و <span dir="ltr">`Dependency Injection`</span>

**کلاس‌های درگیر:** `ElevatorFactory` و `ElevatorService`  
**محل:** متد `ElevatorFactory.createService()`.

</div>

<div dir="ltr">

```java
SimpleDoor door = new SimpleDoor();
InMemoryFloorSensor sensor = new InMemoryFloorSensor(initialFloor);
return new ElevatorService(elevator, motor, door, sensor, new ElevatorEventPublisher());
```

</div>

<div dir="rtl">

**توضیح:** `ElevatorFactory` وابستگی‌ها را می‌سازد و به `ElevatorService` می‌دهد. سرویس به جای ساخت مستقیم آن‌ها، وابستگی‌ها را از بیرون دریافت می‌کند.

### ۲.۲. پکیج `events`

#### P3: <span dir="ltr">`Observer`</span> / <span dir="ltr">`Event Pattern`</span>

**کلاس‌های درگیر:** `ElevatorEventPublisher`، `ElevatorEventListener` و `ElevatorService`  
**محل:** متدهای `subscribe()` و `publish()` در `ElevatorEventPublisher`.

</div>

<div dir="ltr">

```java
public void subscribe(ElevatorEventListener listener) {
    listeners.add(listener);
}

public void publish(String event) {
    for (ElevatorEventListener listener : listeners) listener.onEvent(event);
}
```

</div>

<div dir="rtl">

**توضیح:** سرویس رویدادها را منتشر می‌کند و به مصرف‌کننده مشخصی وابسته نیست. در آزمون می‌توان با یک شنونده ساده، رویدادها یا `Indirect Outputs` را ثبت کرد و `Observability` را افزایش داد.

### ۲.۳. پکیج `hardware`

#### P4: <span dir="ltr">`Adapter`</span> / <span dir="ltr">`Wrapper`</span>

**کلاس‌های درگیر:** `Motor`، `LegacyMotorAdapter` و `LegacyLiftMotor`  
**محل:** متد `LegacyMotorAdapter.moveTo()`.

</div>

<div dir="ltr">

```java
@Override
public void moveTo(int floor) {
    legacyMotor.selectDestinationAndStart(floor);
}
```

</div>

<div dir="rtl">

**توضیح:** `LegacyMotorAdapter` فراخوانی `Motor.moveTo()` را به `LegacyLiftMotor.selectDestinationAndStart()` تبدیل می‌کند. سرویس به رابط `Motor` وابسته می‌ماند و می‌توان موتور دیگری را برای آزمون جایگزین کرد؛ این موضوع به `Decomposability` و `Controllability` کمک می‌کند.

#### P5: <span dir="ltr">`Interface Segregation`</span>

**کلاس‌های درگیر:** `DoorOpener`، `DoorStatusReader` و `SimpleDoor`  
**محل:** فایل‌های `DoorOpener.java`، `DoorStatusReader.java` و تعریف `SimpleDoor`.

</div>

<div dir="ltr">

```java
public interface DoorOpener {
    void open();

    void close();
}
```

</div>

<div dir="ltr">

```java
public interface DoorStatusReader {
    boolean isOpen();
}
```

</div>

<div dir="rtl">

**توضیح:** عملیات باز و بسته کردن در از خواندن وضعیت آن جدا شده است. هر دو رابط را `SimpleDoor` پیاده‌سازی می‌کند و رابط‌های کوچک ساخت `Test Double` را آسان‌تر می‌کنند.

#### P6: <span dir="ltr">`Test Double`</span>: نقش‌های <span dir="ltr">`Test Spy`</span> و <span dir="ltr">`Fake Object`</span> / <span dir="ltr">`Test Stub`</span>

**کلاس‌های درگیر:** `RecordingMotor` و `InMemoryFloorSensor`  
**محل:** متدهای `RecordingMotor.moveTo()`، `RecordingMotor.getLastDestination()` و `InMemoryFloorSensor.setFloor()`.

</div>

<div dir="ltr">

```java
@Override
public void moveTo(int floor) {
    lastDestination = floor;
}

public int getLastDestination() {
    return lastDestination;
}
```

</div>

<div dir="ltr">

```java
public void setFloor(int floor) {
    this.floor = floor;
}
```

</div>

<div dir="rtl">

**توضیح:** `RecordingMotor` در آزمون نقش `Test Spy` دارد و مقصد ثبت‌شده را برای `Observability` در دسترس می‌گذارد. `InMemoryFloorSensor` می‌تواند نقش `Fake Object` یا `Test Stub` داشته باشد و با `setFloor()` مقدار حسگر را برای افزایش `Controllability` تغییر دهد.

### ۲.۴. پکیج‌های `model` و `service`

#### P7: <span dir="ltr">`Dependency Injection`</span> از طریق سازنده

**کلاس‌های درگیر:** `ElevatorService`، `Elevator`، `Motor`، `DoorOpener`، `FloorSensor` و `ElevatorEventPublisher`  
**محل:** سازنده `ElevatorService`.

</div>

<div dir="ltr">

```java
public ElevatorService(Elevator elevator, Motor motor, DoorOpener door,
                       FloorSensor sensor, ElevatorEventPublisher events) {
    this.elevator = elevator;
    this.motor = motor;
    this.door = door;
    this.sensor = sensor;
    this.events = events;
}
```

</div>

<div dir="rtl">

**توضیح:** همه وابستگی‌های سرویس در سازنده دریافت می‌شوند. آزمون می‌تواند وابستگی‌های مناسب را تزریق کند و سرویس را با کنترل بیشتری بررسی کند.

#### P8: مدل مستقل

**کلاس‌های درگیر:** `Elevator` و `ElevatorState`  
**محل:** سازنده `Elevator` و متدهای `getCurrentFloor()` و `getState()`.

</div>

<div dir="ltr">

```java
public Elevator(int currentFloor, int minFloor, int maxFloor) {
    if (minFloor > maxFloor || currentFloor < minFloor || currentFloor > maxFloor)
        throw new IllegalArgumentException("Invalid floor configuration");
    this.currentFloor = currentFloor;
    this.minFloor = minFloor;
    this.maxFloor = maxFloor;
}

public int getCurrentFloor() {
    return currentFloor;
}
```

</div>

<div dir="rtl">

**توضیح:** `Elevator` و `ElevatorState` به وابستگی خارجی نیاز ندارند. سازنده پارامتری به `Controllability` و متدهای خواندن وضعیت به `Observability` کمک می‌کنند.

### ۲.۵. نقاط قوت در `src/test`

در `ElevatorServiceTest.setUp()`، وابستگی‌ها با `Dependency Injection` به سرویس داده می‌شوند و `publisher.subscribe(receivedEvents::add)` رویدادها را با `Observer` ثبت می‌کند. در `LegacyPanelTest.testLegacyMotorAdapterMovesToRequestedFloor()` نیز عملکرد `Adapter` با بررسی مقصد موتور قدیمی آزمون شده است.

## ۳. `Bad Smell`ها و `Anti-Pattern`های شناسایی‌شده

### الف) کد عملیاتی (`src/main`)

#### M1: <span dir="ltr">`Hard-Coded Resource Path` و کاهش `Controllability`

**محل:** فایل `ElevatorConfiguration.java`، متد `load()`.

</div>

<div dir="ltr">

```java
try (InputStream input = getClass().getClassLoader().getResourceAsStream("elevator.properties")) {
    if (input == null) throw new IOException("elevator.properties not found");
    properties.load(input);
}
```

</div>

<div dir="rtl">

**علت مشکل:** نام `elevator.properties` داخل متد ثابت است. آزمون واحد نمی‌تواند به‌راحتی فایل متفاوتی را برای بررسی خطاها یا تنظیمات مختلف تزریق کند.

**پیشنهاد اصلاح:** نام فایل یا `InputStream` را به سازنده یا متد `load()` بدهید. در این صورت آزمون می‌تواند ورودی دلخواه خود را مشخص کند.

#### M2: <span dir="ltr">`Test Logic in Production`</span>؛ احتمال <span dir="ltr">`Test Hook`</span>

**محل:** فایل `ElevatorFactory.java`، بلاک `else` در متد `createService()`.

</div>

<div dir="ltr">

```java
if ("legacy".equalsIgnoreCase(config.getProperty("motor.type", "legacy")))
    motor = new LegacyMotorAdapter(new LegacyLiftMotor());
else
    motor = new RecordingMotor();
```

</div>

<div dir="rtl">

**علت مشکل:** `RecordingMotor` در آزمون نقش `Test Spy` دارد و کارخانه برای هر مقدار غیر از `legacy` آن را انتخاب می‌کند. اگر این کلاس فقط برای آزمون در نظر گرفته شده باشد، ساخت آن در مسیر عملیاتی مصداق احتمالی `Test Logic in Production` است؛ خود کد وابستگی به کتابخانه آزمون یا شرط مخصوص آزمون را نشان نمی‌دهد.

**پیشنهاد اصلاح:** اگر موتور ثبت‌کننده کاربرد عملیاتی ندارد، آن را از مسیر انتخاب عملیاتی حذف کنید. برای مقدار نامعتبر `motor.type` خطای روشن برگردانید.

#### M3: <span dir="ltr">`Explicit Instantiation`</span> و تنظیمات ثابت

**محل:** فایل `ElevatorFactory.java`، متد `createService()`.

</div>

<div dir="ltr">

```java
Properties config = new ElevatorConfiguration().load();
int initialFloor = Integer.parseInt(config.getProperty("initial.floor", "1"));
Elevator elevator = new Elevator(initialFloor, 0, 10);
```

</div>

<div dir="rtl">

**علت مشکل:** ساخت مستقیم `ElevatorConfiguration` وابستگی را پنهان می‌کند و `Controllability` آزمون کارخانه را کاهش می‌دهد. همچنین حداقل و حداکثر طبقه به صورت ثابت در کد مشخص شده‌اند؛ این مقادیر عملیاتی را نباید بدون شواهد `Hard-Coded Test Data` نامید.

**پیشنهاد اصلاح:** امکانی برای دریافت مستقیم `Properties` در `ElevatorFactory` اضافه کنید. در صورت نیاز به تغییر محدوده طبقات، مقدار حداقل و حداکثر را نیز از تنظیمات بخوانید.

#### M4: <span dir="ltr">`Test Logic in Production`</span> / <span dir="ltr">`For Tests Only`</span>؛ مورد احتمالی

**محل:** فایل `RecordingMotor.java`، متد `getLastDestination()`؛ فایل `InMemoryFloorSensor.java`، متد `setFloor()`.

</div>

<div dir="ltr">

```java
public int getLastDestination() {
    return lastDestination;
}
```

</div>

<div dir="ltr">

```java
public void setFloor(int floor) {
    this.floor = floor;
}
```

</div>

<div dir="rtl">

**علت مشکل:** `getLastDestination()` در کد فعلی فقط از آزمون‌ها فراخوانی شده است و `setFloor()` نیز امکان کنترل حسگر را فراهم می‌کند. با این حال هر دو کلاس در ساخت سرویس استفاده می‌شوند؛ بنابراین اختصاصی بودن آن‌ها به آزمون باید با توجه به نقش شبیه‌ساز مشخص شود.

**پیشنهاد اصلاح:** اگر `RecordingMotor` فقط ابزار آزمون است، آن را به `src/test/java` منتقل کنید. برای `InMemoryFloorSensor` نیز ابتدا مشخص کنید تغییر طبقه در شبیه‌سازی عملیاتی لازم است یا فقط در آزمون.

#### M5: <span dir="ltr">`Hidden Dependencies`</span> و استفاده ناقص از <span dir="ltr">`Interface Segregation`</span>

**محل:** فایل `ElevatorService.java`، سازنده و شرط `if` در متد `moveTo()`.

</div>

<div dir="ltr">

```java
if (door instanceof edu.course.elevator.hardware.DoorStatusReader status && status.isOpen())
    throw new IllegalStateException("Close the door before moving");
```

</div>

<div dir="rtl">

**علت مشکل:** سازنده فقط `DoorOpener` را دریافت می‌کند و نیاز به `DoorStatusReader` در شرط متد مشخص می‌شود. اگر `Test Double` فقط `DoorOpener` را پیاده‌سازی کند، بررسی باز بودن در انجام نمی‌شود.

**پیشنهاد اصلاح:** `DoorStatusReader` را به صورت یک وابستگی صریح به سازنده بدهید. سپس وضعیت در را بدون شرط `instanceof` بررسی کنید.

#### M6: <span dir="ltr">`Tight Coupling`</span> و حساسیت به رشته رویداد

**محل:** فایل `ElevatorService.java`، سازنده و متدهای انتشار رویداد؛ فایل `ElevatorEventPublisher.java`.

</div>

<div dir="ltr">

```java
private final ElevatorEventPublisher events;
```

</div>

<div dir="ltr">

```java
events.publish("ARRIVED:" + destination);
```

</div>

<div dir="rtl">

**علت مشکل:** سرویس به کلاس عینی `ElevatorEventPublisher` وابسته است و رویداد را به صورت رشته منتشر می‌کند. آزمون بررسی‌کننده متن رویداد به قالب دقیق آن وابسته می‌شود؛ این نشانه به‌تنهایی برای اثبات `Sensitive Equality` کافی نیست.

**پیشنهاد اصلاح:** برای ناشر یک `Interface` تعریف کنید تا پیاده‌سازی آن قابل جایگزینی باشد. برای رویدادها از یک نوع مشخص مانند `enum` یا کلاس رویداد استفاده کنید.

#### M7: ناهماهنگی وضعیت طبقه و کاهش <span dir="ltr">`Observability`</span>

**محل:** فایل `ElevatorService.java`، متدهای `moveTo()` و `getCurrentFloor()`.

</div>

<div dir="ltr">

```java
motor.moveTo(destination);
elevator.moveTo(destination);
events.publish("ARRIVED:" + destination);
```

</div>

<div dir="ltr">

```java
public int getCurrentFloor() {
    return sensor.readCurrentFloor();
}
```

</div>

<div dir="rtl">

**علت مشکل:** `moveTo()` به موتور فرمان می‌دهد و مدل را تغییر می‌دهد، اما حسگر حافظه‌ای را به‌روز نمی‌کند. پس از حرکت، مقدار خوانده‌شده از حسگر می‌تواند با طبقه مدل متفاوت باشد.

**پیشنهاد اصلاح:** منبع اصلی طبقه فعلی را در قرارداد شبیه‌ساز مشخص کنید. سپس وضعیت مدل و حسگر و زمان اعلام رسیدن را بر اساس همان قرارداد هماهنگ کنید.

### ب) کد آزمون (`src/test`)

#### T1: <span dir="ltr">`Obscure Test`</span> / <span dir="ltr">`Eager Test`</span> و <span dir="ltr">`Hard-Coded Test Data`</span>

**محل:** فایل `ElevatorServiceTest.java`، متد `testMoveOpenCloseAndNotifyEverything()`.

</div>

<div dir="ltr">

```java
service.moveTo(4);
assertEquals(4, elevator.getCurrentFloor());
assertEquals(4, motor.getLastDestination());
assertEquals(ElevatorState.IDLE, elevator.getState());
assertTrue(receivedEvents.contains("ARRIVED:4"));

service.openDoor();
assertTrue(door.isOpen());
assertEquals(ElevatorState.DOOR_OPEN, elevator.getState());
assertTrue(receivedEvents.contains("DOOR_OPENED"));

service.closeDoor();
assertFalse(door.isOpen());
assertEquals(ElevatorState.IDLE, elevator.getState());
assertTrue(receivedEvents.contains("DOOR_CLOSED"));
```

</div>

<div dir="rtl">

**علت مشکل:** سه رفتار حرکت، باز کردن در و بستن در در یک آزمون جمع شده‌اند و پیدا کردن علت شکست را دشوار می‌کنند. عدد ۴ نیز چند بار تکرار شده است؛ البته ثابت بودن عدد به‌تنهایی مشکل قطعی نیست.

**پیشنهاد اصلاح:** برای هر رفتار یک متد `@Test` جداگانه بنویسید. برای مقصد از نامی گویا استفاده کنید و نتیجه مورد انتظار را روشن نگه دارید.

#### T2: <span dir="ltr">`Obscure Test`</span> / <span dir="ltr">`Mystery Guest`</span> و پیچیدگی کد آزمون

**محل:** فایل `ElevatorServiceTest.java`، متد `testMoveToFloorFromFixtureFile()`؛ فایل `src/test/resources/floors.csv`.

</div>

<div dir="ltr">

```java
var stream = getClass().getClassLoader().getResourceAsStream("floors.csv");
assertNotNull(stream);
String firstDataLine;
try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(stream))) {
    firstDataLine = reader.lines().filter(line -> !line.startsWith("#")).findFirst().orElseThrow();
}
int floor = Integer.parseInt(firstDataLine.split(",")[0].trim());
service.moveTo(floor);
assertEquals(floor, elevator.getCurrentFloor());
```

</div>

<div dir="rtl">

**علت مشکل:** ورودی در فایل بیرونی است و اولین داده آن ۱، برابر با طبقه اولیه است؛ بنابراین تغییر طبقه بررسی نمی‌شود. منطق خواندن فایل نیز فهم آزمون را دشوارتر می‌کند، هرچند وجود آن به‌تنهایی اثبات `Untestable Test Code` نیست.

**پیشنهاد اصلاح:** برای آزمون حرکت، ورودی صریحی متفاوت از طبقه اولیه به کار ببرید. اگر بررسی خواندن فایل هم لازم است، برای آن آزمون جداگانه در نظر بگیرید.

#### T3: <span dir="ltr">`Obscure Test`</span> / <span dir="ltr">`Hard-Coded Test Data`</span>

**محل:** فایل `ElevatorServiceTest.java`، متدهای `testMoveToHardCodedFloor()`، `testRejectsOutOfRangeFloor()` و `testDoorMustBeClosedBeforeMoving()`.

</div>

<div dir="ltr">

```java
service.moveTo(7);
assertEquals(7, elevator.getCurrentFloor());
```

</div>

<div dir="ltr">

```java
assertThrows(IllegalArgumentException.class, () -> service.moveTo(11));
```

</div>

<div dir="ltr">

```java
service.openDoor();
assertThrows(IllegalStateException.class, () -> service.moveTo(3));
```

</div>

<div dir="rtl">

**علت مشکل:** نقش بعضی اعداد، مانند ارتباط ۱۱ با حداکثر طبقه ۱۰، بدون توجه به آماده‌سازی آزمون روشن نیست. نام `testMoveToHardCodedFloor()` نیز به شیوه نوشتن آزمون اشاره می‌کند؛ وجود عدد ثابت به‌تنهایی نشانه قطعی `Smell` نیست.

**پیشنهاد اصلاح:** برای ورودی‌های مهم نام‌های توصیفی مانند `outOfRangeFloor` انتخاب کنید. نام آزمون را نیز بر اساس رفتار مورد انتظار تغییر دهید.

#### T4: <span dir="ltr">`Conditional Test Logic`</span> / <span dir="ltr">`Conditional Verification Logic`</span>

**محل:** فایل `ElevatorServiceTest.java`، متد `testSensorReadingWithConditionalVerification()`.

</div>

<div dir="ltr">

```java
int observedFloor = service.getCurrentFloor();
if (observedFloor == 1) {
    assertEquals(1, observedFloor);
} else {
    assertEquals(0, observedFloor);
}
```

</div>

<div dir="rtl">

**علت مشکل:** آزمون بر اساس مقدار واقعی تصمیم می‌گیرد چه انتظاری را بررسی کند. در نتیجه خروجی نادرست صفر نیز می‌تواند باعث موفقیت آزمون شود.

**پیشنهاد اصلاح:** شرط را حذف کنید و مقدار حسگر را با یک `assertEquals` مستقیم بررسی کنید. انتظار را پیش از اجرای سرویس مشخص کنید.

#### T5: <span dir="ltr">`Conditional Test Logic`</span> / <span dir="ltr">`Multiple Test Conditions`</span>

**محل:** فایل `ElevatorServiceTest.java`، متد `testSeveralDestinationsInOneMethod()`.

</div>

<div dir="ltr">

```java
assertEquals(2, moveAndRead(2));
assertEquals(6, moveAndRead(6));
assertEquals(9, moveAndRead(9));
```

</div>

<div dir="rtl">

**علت مشکل:** سه مقصد پشت سر هم روی یک نمونه بررسی می‌شوند؛ با شکست هر مورد، موارد بعدی اجرا نمی‌شوند. این مورد در دسته `Multiple Test Conditions` اسلاید قرار می‌گیرد، هرچند متد شرط `if` ندارد.

**پیشنهاد اصلاح:** حالت‌ها را با `@ParameterizedTest` یا چند `@Test` مستقل بررسی کنید. اگر توالی حرکت‌ها هم مهم است، برای آن آزمون جداگانه نگه دارید.

#### T6: <span dir="ltr">`Obscure Test`</span> / <span dir="ltr">`Mystery Guest`</span> و احتمال <span dir="ltr">`Indirect Testing`</span>

**محل:** فایل `FactoryTest.java`، متد `factoryBuildsAWorkingElevatorService()`.

</div>

<div dir="ltr">

```java
var service = new ElevatorFactory().createService();
service.moveTo(6);
assertEquals(6, service.getElevator().getCurrentFloor());
```

</div>

<div dir="rtl">

**علت مشکل:** مقادیر `elevator.properties` در متن آزمون دیده نمی‌شوند و نتیجه از طریق اجرای سرویس بررسی می‌شود. اگر هدف آزمون فقط ساخت کارخانه باشد، بررسی غیرمستقیم رفتار سرویس جداسازی علت خطا را دشوار می‌کند؛ عدد ۶ نیز یک ورودی ثابت است.

**پیشنهاد اصلاح:** پس از قابل تزریق شدن تنظیمات، `Properties` را صریح در آزمون بسازید. آزمون ساخت کارخانه و آزمون رفتار سرویس را بر اساس هدف هر کدام تفکیک کنید.

#### T7: <span dir="ltr">`Test Code Duplication`</span> / <span dir="ltr">`Cut-and-Paste Code Reuse`</span> و <span dir="ltr">`Hard-Coded Test Data`</span>

**محل:** فایل `LegacyPanelTest.java`، متدهای `testRecordingMotorMovesToRequestedFloor()`، `testAnotherRecordingMotorScenario()` و `testLegacyMotorAdapterMovesToRequestedFloor()`.

</div>

<div dir="ltr">

```java
@Test
void testRecordingMotorMovesToRequestedFloor() {
    RecordingMotor motor = new RecordingMotor();
    motor.moveTo(3);
    assertEquals(3, motor.getLastDestination());
}

@Test
void testAnotherRecordingMotorScenario() {
    RecordingMotor motor = new RecordingMotor();
    motor.moveTo(5);
    assertEquals(5, motor.getLastDestination());
}
```

</div>

<div dir="rtl">

**علت مشکل:** دو آزمون موتور ثبت‌کننده ساختار تکراری دارند و فقط مقصد آن‌ها فرق می‌کند؛ نام آزمون دوم نیز هدف آن را روشن نمی‌کند. مقادیر ثابت ۳، ۵ و ۸ به کار رفته‌اند، هرچند به‌تنهایی نشانه قطعی `Smell` نیستند.

**پیشنهاد اصلاح:** دو حالت تکراری را با `@ParameterizedTest` یا یک متد کمکی ساده بررسی کنید. نام آزمون‌ها و ورودی‌های مهم را گویا انتخاب کنید.

## ۴. جدول جمع‌بندی نهایی

علامت «—» یعنی در این گزارش مورد مستقلی برای فایل ثبت نشده است. ارجاع‌های `P`، `M` و `T` به یافته‌های بخش‌های قبل اشاره دارند.

| نام فایل | `Design Pattern` / نقش مثبت | `Bad Smell` / `Anti-Pattern` |
|---|---|---|
| `ElevatorConfiguration.java` | P1: `Configuration by Contract` | M1، M3 |
| `elevator.properties` | P1: تنظیمات خارجی | T6: وابستگی آزمون به تنظیمات |
| `ElevatorFactory.java` | P1 و P2: تنظیمات و ساخت وابستگی‌ها | M2، M3 |
| `ElevatorEventListener.java` | P3: رابط `Observer` | — |
| `ElevatorEventPublisher.java` | P3: انتشار رویداد | M6 |
| `DoorOpener.java` | P5: `Interface Segregation` | — |
| `DoorStatusReader.java` | P5: `Interface Segregation` | — |
| `SimpleDoor.java` | P5: پیاده‌سازی رابط‌های در | — |
| `FloorSensor.java` | P7: وابستگی قابل تزریق | M7 |
| `InMemoryFloorSensor.java` | P6: کنترل مقدار حسگر | M4، M7 |
| `Motor.java` | P4 و P7: رابط موتور | — |
| `LegacyLiftMotor.java` | P4: موتور قدیمی | — |
| `LegacyMotorAdapter.java` | P4: `Adapter` | — |
| `RecordingMotor.java` | P6: نقش `Test Spy` | M2، M4، T7 |
| `Elevator.java` | P8: کنترل و مشاهده وضعیت مدل | M7 |
| `ElevatorState.java` | P8: نمایش وضعیت مدل | — |
| `ElevatorService.java` | P3 و P7: رویداد و `Dependency Injection` | M5، M6، M7 |
| `ElevatorServiceTest.java` | `Dependency Injection` و `Observer` در آماده‌سازی | T1 تا T5 |
| `floors.csv` | داده ورودی آزمون | T2: `Mystery Guest` |
| `FactoryTest.java` | آزمون ساخت سرویس | T6 |
| `LegacyPanelTest.java` | آزمون `Adapter` | T7 |

</div>
