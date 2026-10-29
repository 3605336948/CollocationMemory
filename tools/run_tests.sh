#!/usr/bin/env bash
# 跑核心逻辑单元测试
#
# 为什么不用 gradle test：本机用户名是中文路径，Gradle 8.4 的测试 worker
# 在那里起不来（ClassNotFoundException: GradleWorkerMain）。这里先让 Gradle
# 编译并导出 classpath，然后在同一个 JVM 里直接跑 JUnitCore，绕开 worker。
set -e
cd "$(dirname "$0")/.."

GRADLE="/c/Users/王佳豪/.gradle/wrapper/dists/gradle-8.4-bin/cnqr4qbc93ir6sp626e40kx2s/gradle-8.4/bin/gradle.bat"
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"

# bundleDebugClassesToRuntimeJar 会刷新主代码打成的 classes.jar（run_tests.sh 的
# 运行时 classpath 第一项就是这个 jar）。只跑 compileDebugUnitTestKotlin 不会重建它，
# 改了 main 代码再跑测试会链接到旧 main 类 → NoSuchMethodError。所以先刷新。
"$GRADLE" bundleDebugClassesToRuntimeJar compileDebugUnitTestKotlin printTestCp --console=plain -q
CP=$(cat tools/test-cp.txt)
exec "$JAVA_HOME/bin/java" -Dfile.encoding=UTF-8 \
  -cp "app/build/tmp/kotlin-classes/debugUnitTest;$CP" \
  org.junit.runner.JUnitCore com.example.collocationmemory.CoreLogicTest
