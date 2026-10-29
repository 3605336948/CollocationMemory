plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.example.collocationmemory"
    compileSdk = 35

    /**
     * 内置词库 app_library.json 放在项目的 tools/assets/ 目录下，
     * 通过 assets 源目录配置打进 APK（应用内用 assets.open("app_library.json") 读取）。
     */
    sourceSets {
        getByName("main") {
            assets.srcDirs("src/main/assets", "../tools/assets")
        }
    }

    defaultConfig {
        applicationId = "com.example.collocationmemory"
        /**
         * 低版本兼容包开关（默认关闭，主线不受影响）。
         *
         * 背景：minSdk = 35 意味着「只允许 Android 15 及以上安装」，
         * Android 15 以下的老设备（如鸿蒙 4.x 平板）装上会报「解析包时出现问题」。
         * 主线版本保持不变，只在需要时用下面这条命令另打一个兼容包：
         *
         *   gradlew assembleRelease -PlegacyCompat
         *
         * 产物：app/build/outputs/apk/release/app-release.apk，minSdk 26（Android 8.0）。
         * 兼容包与主线同 versionCode（见下方 versionCode 注释），
         * 因此不会互相触发「发现新版本」的更新提示。
         */
        val legacyCompat = project.hasProperty("legacyCompat")
        minSdk = if (legacyCompat) 26 else 35
        targetSdk = 35
        // 已发布过 1.0(1)、1.1(2)、1.2(3)、1.3(4)、1.4(5)、1.5(6)
        // 1.6(7)：支持多词本；现有 187 条归入「蓝哥固定搭配单词本」
        // 2.0(11)：复习排期改为按自然日（第二天零点到期）；壁纸改为每天一换
        // 2.1(12)：加入第二个词本「往年固定搭配单词本」（PDF 提取 1413 条）
        // 2.2(13)：启动时展示「版权与使用声明」弹窗；声明文字三处埋点作为维权证据
        // 2.3(14)：更换 App 图标（蓝色六边形 + 学士帽 + 书本）
        // 2.4(15)：修正版权声明的措辞（区分软件程序与学习资料的两种权利）
        // 2.5(16)：蓝哥本并入「固定搭配4」54 条，187 -> 241 条
        // 2.6(17)：首页新增「蓝人单词对战」入口（跳转外部链接）；修复跨词本重复播种的 bug
        // 2.7(18)：对战入口移到 Learn/Review 同一行，图标与文字改为竖排主题样式
        // 2.8(19)：对战卡上抬一截（不影响 Learn/Review 尺寸）
        // 2.9(20)：对战入口改为贴右侧悬浮（竖直居中偏下），Learn/Review 恢复整行等宽
        // 3.0(21)：壁纸铺满整屏、导航栏改半透明（消除底部黑边）
        // 3.1(22)：Learn/Review 改为半透明磨砂玻璃 + 黑字（贴近参考图）
        // 3.2(23)：对战入口改成可拖动的悬浮图标（去文字、换图、位置可存）
        // 3.3(24)：新增两个词本「英语四级核心词汇」1484 条、「四六高频词汇」686 条
        // 3.4(25)：新学/复习页改用当日壁纸背景 + 磨砂玻璃卡片（与首页统一）
        // 3.5(26)：选项按钮改为圆润大圆角（22dp），去掉生硬的白框
        // 3.6(27)：修复拼写判定 —— 省略号/占位符/括号/冠词不再判错（影响约 184 条）
        // 3.7(28)：词库订正通道 —— 修正 steps to do 截断 / play an key 语法错，老用户也能收到
        // 3.8(29)：应用内更新 —— 启动自动检查新版本，App 内下载并跳装；
        //          词库/释义改为联网热更新，不用重装就能加词、改释义
        // 3.9(30)：修 3.8 的限流缺陷 —— 整包弹窗与词库热更新原本共用 6 小时限流，
        //          导致刚推送的词库学生要等最多 6 小时才生效；现拆开：整包仍 6 小时，
        //          词库改为 30 分钟一次（清单仅 2KB，版本没变就不下词库）
        // 4.0(31)：三个新功能 —— ① 新学卡片顶栏「熟」标记：直接排入复习、跳过拼写；
        //          ② 复习完可选「拼写全部 / 只拼写记错的」；③ 笔记本：生词本图标右上角
        //          小三角抽屉进入，学/复习卡片「记」收藏，内容本地存储、可改可删
        // 4.1(32)：修复「切后台丢进度」—— 学习/复习会话原本只活在内存里，
        //          华为/鸿蒙切后台杀进程后整组消失，且答过的词已排到明天，
        //          重进只能拿到下一批，看着就像「刚才那组全没了」。现在会话
        //          （词表/进度/拼写断点/记错名单）实时落盘，首页给「继续上次」入口，
        //          点新学/复习也优先续做未完成的那一组。
        // 4.1.1(33)：修复 4.1 会话恢复的两个遗留问题 ——
        //          ① 恢复的会话没重设计时起点，结算时把「1970 年至今的分钟数」
        //          （约 2900 万）写进今日/累计时长（首页显示成天文数字）；
        //          ② 自动修复已写坏的记录：被污染的每日时长夹到 30 分钟，
        //          累计时长按每日记录重算（只跑一次）。另加防御：结算上限 12 小时。
        // 4.2(34)：单词朗读 —— 新学/复习翻到一张卡片自动念一遍英文，
        //          顶栏「英」按钮可随时再听一次；设置页「自动朗读」开关
        //          （关掉后卡片不出声，手动按钮仍可用）。设备没有英文语音
        //          数据时静默降级 + 提示一次，不影响背单词。
        // 4.3(35)：① 内置词库 v9 —— 蓝哥固定搭配单词本新增「固定搭配6」74 条；
        //          ② 蓝哥本、往年本词条的学习/复习卡片盖浅色版权水印
        //          「资料版权归湖北小蓝书教育科技有限公司」（其他词本不显示）；
        //          ③ 版权声明里的资料权利人统一改为湖北小蓝书教育科技有限公司
        // 4.3.1(36)：新学算法缺陷修复 —— 本组答错过的词需连续答对 2 次才算学会、
//          强制拼写（标「熟」也不能跳过）、首次复习间隔 2 天起；未答错过的词规则不变
// 4.3.2(37)：新学算法再调整 —— 连对 2 次推广到所有新学卡片（不论是否答错都需连对 2 次才算学会）、
//          取消强制拼写（顶栏「熟」所有词都可跳过拼写）；答错过的词首次复习间隔仍 2 天
// 4.3.3(38)：新学页改为「认识 / 模糊 / 不认识」三键自评（去掉四选一选释义），
//          卡片做成沉浸式：白字直接压在模糊每日壁纸上；拼写巩固页同步同一套风格；
//          拼写空输入点回车/提交只提示「请输入内容」，不再直接出答案；
//          界面降噪（进度条改发丝线、「记」角标改描边并上移避开长词组）；
//          修复再次点「新学」时被上一次遗留状态推去拼写页的问题；
//          词库升到 v11：固定搭配7 的 63 个搭配全部进入蓝哥固定搭配单词本
// 4.3.4(39)：拼写判定收紧（宁严勿宽）—— 任何字母拼错都判错：
//          多写/漏写单词、多打字母（pay attention tos）、同长度错字母（gather jn）
//          一律不再判对；仅保留格式差异容错（大小写/空格/标点/冠词/占位符/连字符空格混用）
// 兼容包（-PlegacyCompat）不另占版本号：与主线同 versionCode / versionName，
        // 只是 minSdk 从 35 放宽到 26，让 Android 15 以下的老设备也能装。
        // 两者互不触发更新提示（versionCode 相同，App 判定为「已是最新」）。
        // 以后每次发版只需把 versionCode +1，必须始终大于已发出去的包
        versionCode = 40
        versionName = if (project.hasProperty("legacyCompat")) "4.3.5-兼容版" else "4.3.5"

        // ============================================================
        // 【版权与使用声明】—— 随 APK 一同分发，作为权利归属证据
        //
        // 本软件为完全免费软件，无任何收费项目，
        // 禁止任何人私自售卖、倒卖本软件牟利。
        //
        // 未经作者书面许可，禁止对本软件进行二次开发、逆向、解包、
        // 修改后分发转载。
        //
        // 软件程序（代码、界面、功能）版权归软件作者所有。
        // 软件内学习资料（词库、释义、例句等）版权归湖北小蓝书教育科技有限公司所有，
        // 由该公司授权本软件内置使用，仅供个人学习交流。
        //
        // 违反本声明进行商用倒卖、二次改版分发，将追究相应责任。
        //
        // 上述文字同时存在于：
        //   - app/src/main/java/.../legal/CopyrightNotice.kt（源码）
        //   - app/src/main/res/raw/copyright_notice.txt（打包资源）
        //   - tools/copyright_notice.txt（仓库）
        // 反编译任何二次分发的 APK，只要包含上述任一内容，
        // 即可证明其来源于本工程。构建时间见 buildConfigField。
        //
        // 注意：这里刻意区分「软件程序」与「学习资料」两种权利。
        // 程序是作者自己写的，可以主张版权；资料的权利人是湖北小蓝书
        // 教育科技有限公司（经授权内置），作者并非资料权利人，
        // 不能声称「资料版权归作者所有」。
        // ============================================================
        buildConfigField("String", "SOFTWARE_AUTHOR", "\"搭配记忆作者（QQ 3605336948）\"")
        buildConfigField("String", "SOFTWARE_NAME", "\"搭配记忆\"")
        buildConfigField("String", "MATERIALS_SOURCE", "\"湖北小蓝书教育科技有限公司\"")
        buildConfigField(
            "String",
            "COPYRIGHT_NOTICE",
            "\"本软件为完全免费软件，禁止私自售卖、倒卖、二次改版分发；\" + " +
                "\"软件程序版权归作者所有；\" + " +
                "\"学习资料版权归湖北小蓝书教育科技有限公司所有，仅供个人学习交流使用。\""
        )

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file("../release.keystore")
            storePassword = (project.findProperty("RELEASE_STORE_PASSWORD") as String?) ?: "collocation2026"
            keyAlias = (project.findProperty("RELEASE_KEY_ALIAS") as String?) ?: "collocation"
            keyPassword = (project.findProperty("RELEASE_KEY_PASSWORD") as String?) ?: "collocation2026"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            // 关键：debug 也用 release 密钥签名。
            // 否则 debug 包和 release 包签名不同，覆盖安装会被系统拒绝，
            // 只能先卸载 —— 一卸载，用户的学习记录就全没了。
            // 用同一把密钥后，两种包可以互相覆盖安装，数据始终保留。
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        // 启用 BuildConfig：版权声明通过 buildConfigField 随包分发，
        // 反编译 APK 即可看到权利归属文字（维权证据之一）。
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.10"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation(platform("androidx.compose:compose-bom:2024.04.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // Gson for JSON import
    implementation("com.google.code.gson:gson:2.10.1")

    // ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.04.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

// 打印单元测试的运行期 classpath（Gradle 的 test worker 在中文路径下起不来，
// 用它手工在同一个 JVM 里跑 JUnit）
tasks.register("printTestCp") {
    doLast {
        val cp = configurations.getByName("debugUnitTestRuntimeClasspath").files
            .joinToString(File.pathSeparator) { it.absolutePath }
        val out = File(rootProject.projectDir, "tools/test-cp.txt")
        out.writeText(cp)
        println("classpath 已写入 ${out.absolutePath}（${cp.length} 字符）")
    }
}
