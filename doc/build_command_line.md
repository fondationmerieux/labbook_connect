# Build LabBook Connect from command line

Goal: reproduce an **Eclipse Build Project + Export Runnable JAR** from a cloned repository.

Requirements:
- repository cloned
- `bin/labbook_connect.jar` available (reference JAR containing dependencies)
- JDK 21 installed
- `java`, `javac` and `jar` available in PATH

## 1) Prepare build directories

```bash
mkdir -p target/classes eclipse_lib eclipse_loader
```

## 2) Save original Eclipse MANIFEST

The original MANIFEST contains the Eclipse JAR-in-JAR configuration and must be kept before rebuilding the JAR.

```bash
jar xf bin/labbook_connect.jar META-INF/MANIFEST.MF
```

Check:

```bash
grep Rsrc-Class-Path META-INF/MANIFEST.MF
```

Expected result:

```text
Rsrc-Class-Path: ./ jetty-server-11.0.18.jar ...
```

## 3) Extract embedded libraries

Extract all dependencies included in the reference Runnable JAR:

```bash
cd eclipse_lib
jar xf ../bin/labbook_connect.jar
rm -rf META-INF org labbook_connect plugin logback.xml
cd ..
```

Check:

```bash
ls eclipse_lib/*.jar | wc -l
```

## 4) Extract Eclipse JAR-in-JAR loader

The Eclipse Runnable JAR uses its own loader to start embedded libraries.

```bash
cd eclipse_loader
jar xf ../bin/labbook_connect.jar org
cd ..
```

## 5) Compile Java sources

Equivalent to Eclipse "Build Project":

```bash
javac -cp "eclipse_lib/*" -d target/classes $(find src/main/java -name "*.java")
```

## 6) Copy resources

Copy application resources into compiled classes:

```bash
cp src/main/resource/logback.xml target/classes/
```

This keeps the expected Logback format:

```text
2026-07-09 07:50:16 | App | Line:49 | ...
```

Without this step, Logback will use its default configuration.

## 7) Generate Runnable JAR

Equivalent to Eclipse "Export Runnable JAR":

```bash
jar cfm bin/labbook_connect.jar META-INF/MANIFEST.MF -C eclipse_loader . -C target/classes . -C eclipse_lib .
```

## 8) Verify JAR content

```bash
jar tf bin/labbook_connect.jar | head -20
```

Expected result:

```text
META-INF/
META-INF/MANIFEST.MF
org/
org/eclipse/
org/eclipse/jdt/internal/jarinjarloader/
labbook_connect/
plugin/
```

## 9) Verify final MANIFEST

```bash
rm -rf META-INF
jar xf bin/labbook_connect.jar META-INF/MANIFEST.MF
grep Rsrc-Class-Path META-INF/MANIFEST.MF
```

Expected result:

```text
Rsrc-Class-Path: ./ jetty-server-11.0.18.jar ...
```

## Result

The generated file:

```text
bin/labbook_connect.jar
```

is equivalent to the Eclipse Runnable JAR export with:
- application classes
- resources
- Eclipse JAR-in-JAR loader
- embedded dependencies