package com.example.collocationmemory.legal

/**
 * 版权与使用声明 —— 本文件的文字同时用于四处，构成完整的权利主张：
 *
 * 1. App 启动时弹出的声明弹窗（必须点击「同意」才能进入）
 * 2. 打包进 APK 的 `res/raw/copyright_notice.txt`（随安装包分发，可提取比对）
 * 3. `app/build.gradle.kts` 的 buildConfigField（生成 BuildConfig 常量）
 * 4. 仓库里的 `tools/copyright_notice.txt`
 *
 * 维权要点（若发现倒卖 / 二次改版分发）：
 * - 本文件在源码仓库中带有提交历史，可证明创作时间与作者身份；
 * - 反编译任何二次分发的 APK，只要包含本文件或 res/raw 下的同名字符串，
 *   即可证明其来源于本工程；
 * - [SOFTWARE_AUTHOR] 是软件程序的权利标识。
 *
 * ⚠️ 措辞上的一个重要区分（不要改回去）：
 * - **软件程序**（代码、界面、功能）的权利属于作者，可以明确主张；
 * - **学习资料内容**（词库、释义、例句）的权利属于资料提供方
 *   [MATERIALS_SOURCE]，本软件只是获得授权后内置使用，
 *   作者不能、也不应声称「资料版权归作者所有」——那样反而在纠纷中站不住脚。
 *
 * ⚠️ 修改本文件时，请同步更新 `tools/copyright_notice.txt` 与
 * `app/src/main/res/raw/copyright_notice.txt`，
 * 三者内容由 `tools/dev/check_copyright_sync.py` 校验一致性。
 */
object CopyrightNotice {

    /** 软件名（权利标识之一） */
    const val SOFTWARE_NAME = "搭配记忆"

    /** 软件作者标识：用可联系到的身份，便于维权时主张权利 */
    const val SOFTWARE_AUTHOR = "搭配记忆作者（QQ 3605336948）"

    /**
     * 学习资料的版权归属方（由资料提供方授权内置，不是本软件的作者）。
     * ⚠️ 这是「资料权利人」而非「软件权利人」，两者不要混为一谈。
     */
    const val MATERIALS_SOURCE = "湖北小蓝书教育科技有限公司"

    /** 首次启动是否必须弹出声明（true = 必须同意才能进入） */
    const val REQUIRE_ACCEPTANCE_ON_FIRST_LAUNCH = true

    /**
     * 声明正文。每条是一个段落，UI 按段落渲染；
     * 其中包含「完全免费软件」的段落需要把该短语标红强调（见 [RED_PHRASE]）。
     */
    val PARAGRAPHS: List<String> = listOf(
        "本软件为完全免费软件，无任何收费项目。禁止任何人私自售卖、倒卖本软件牟利。",
        "未经作者书面许可，禁止对本软件进行二次开发、逆向、解包、修改后分发转载。",
        "软件程序（代码、界面、功能）版权归 $SOFTWARE_AUTHOR 所有。",
        "软件内学习资料（词库、释义、例句等）版权归 $MATERIALS_SOURCE 所有，由该公司授权本软件内置使用，仅供个人学习交流，禁止转载、转售或用于任何商业用途。",
        "违反本声明进行商用倒卖、二次改版分发，将追究相应责任。",
    )

    /** 正文中需要用红色强调的短语 */
    const val RED_PHRASE = "完全免费软件"

    /** 弹窗标题 */
    const val TITLE = "版权与使用声明"

    /**
     * 纯文本版本，用于写进 `res/raw/copyright_notice.txt` 与打包注释。
     * 与 [PARAGRAPHS] 必须保持一致。
     */
    val PLAIN_TEXT: String = buildString {
        appendLine("【$TITLE】")
        PARAGRAPHS.forEach { appendLine(it) }
        appendLine()
        appendLine("软件名称：$SOFTWARE_NAME")
        appendLine("软件作者：$SOFTWARE_AUTHOR")
        appendLine("学习资料版权：$MATERIALS_SOURCE（授权内置，仅供个人学习交流使用）")
        append("本声明随本软件一同分发，适用于本软件的任意副本与衍生版本。")
    }
}
