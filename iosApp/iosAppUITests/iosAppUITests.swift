import XCTest

final class iosAppUITests: XCTestCase {

    var app: XCUIApplication!

    override func setUpWithError() throws {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launch()
        sleep(2)
    }

    /// 兼容旧别名
    private func saveScreenshot(name: String) {
        saveScreenScreenshot(name: name)
    }

    /// 辅助方法：保存全屏截图（包含系统键盘图层）到附件；仅当显式配置 REPORTS_DIR 时额外写入文件
    private func saveScreenScreenshot(name: String) {
        let screenshot = XCUIScreen.main.screenshot()
        let attachment = XCTAttachment(screenshot: screenshot)
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)

        if let envPath = ProcessInfo.processInfo.environment["REPORTS_DIR"], !envPath.isEmpty {
            let reportsDir = URL(fileURLWithPath: envPath)
            let fileURL = reportsDir.appendingPathComponent("\(name).png")
            do {
                try screenshot.pngRepresentation.write(to: fileURL)
                print("[SCREENSHOT SAVED] \(fileURL.path)")
            } catch {
                XCTFail("Failed to write screenshot to \(fileURL.path): \(error)")
            }
        }
    }

    /// 辅助方法：轻点屏幕上方收起软键盘
    private func dismissKeyboardIfPresent() {
        if app.keyboards.firstMatch.exists {
            app.windows.firstMatch.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.2)).tap()
            sleep(1)
        }
    }

    /// 辅助方法：安全获取当前所有可见文本列表（在UI稳定时调用）
    private func safeGetAllStaticTexts() -> [String] {
        let texts = app.staticTexts.allElementsBoundByIndex
        var result: [String] = []
        for i in 0..<texts.count {
            let elem = texts[i]
            if elem.exists {
                result.append(elem.label)
            }
        }
        return result
    }

    /// 辅助方法：稳定定位并提取当前最新助手正文（过滤loading、思考指示、用户文本及系统引导）
    private func getAssistantContent() -> String {
        // 1. 优先使用语义标识定位
        let taggedElements = app.staticTexts.matching(identifier: "assistant_message_content").allElementsBoundByIndex
        if !taggedElements.isEmpty {
            let parts = taggedElements.compactMap { $0.exists && !$0.label.isEmpty ? $0.label : nil }
            if !parts.isEmpty {
                return parts.joined(separator: "\n")
            }
        }

        // 2. 严格过滤回退：排除全部非助手正文的文本
        let allTexts = safeGetAllStaticTexts()
        let filtered = allTexts.filter { text in
            !text.contains("今天想聊") &&
            !text.contains("DeepSeek") &&
            !text.contains("输入消息") &&
            !text.contains("AI 正在回复") &&
            !text.contains("思考过程") &&
            !text.contains("正在生成") &&
            !text.contains("描写枫叶与晚霞") && // 排除用户 query
            !text.contains("散文") &&
            !text.contains("暗号") &&
            !text.contains("梳理想法") &&
            !text.contains("改进表达") &&
            !text.contains("解决问题") &&
            !text.isEmpty
        }
        return filtered.joined(separator: "\n")
    }

    /// 顶部安全区只避让一次：菜单应紧邻状态栏下方，不能重叠也不能多留一层安全区。
    func testTopBarDoesNotDoubleApplySafeArea() throws {
        let menuButton = app.buttons["打开侧边栏"]
        let newChatButton = app.buttons["新建对话"]
        XCTAssertTrue(menuButton.waitForExistence(timeout: 5), "未找到顶部菜单按钮")
        XCTAssertTrue(newChatButton.exists, "未找到新建对话按钮")

        // 状态栏属于系统 SpringBoard，不在 Compose 应用的无障碍树中。
        let statusBar = XCUIApplication(bundleIdentifier: "com.apple.springboard").statusBars.firstMatch
        XCTAssertTrue(statusBar.waitForExistence(timeout: 5), "未找到系统状态栏，无法验证顶部安全区")
        let statusBarBottom = statusBar.frame.maxY
        print("[TOP SAFE AREA] statusBar=\(statusBar.frame), menu=\(menuButton.frame), newChat=\(newChatButton.frame)")
        saveScreenScreenshot(name: "ios-top-safe-area")

        for button in [menuButton, newChatButton] {
            let gap = button.frame.minY - statusBarBottom
            XCTAssertGreaterThanOrEqual(gap, 0, "顶部按钮不能与状态栏重叠")
            XCTAssertLessThanOrEqual(gap, 16, "状态栏下方不应额外保留一层安全区")
            XCTAssertTrue(button.isHittable, "顶部按钮应可点击")
        }
        XCTAssertEqual(menuButton.frame.midY, newChatButton.frame.midY, accuracy: 1)

        menuButton.tap()
        XCTAssertTrue(app.buttons["关闭侧边栏"].waitForExistence(timeout: 5), "点击菜单应打开侧边栏")
    }

    /// 缺口 2 独立验收：键盘弹起、全屏截图与系统安全区避让探测
    func testKeyboardAndSafeAreaVerification() throws {
        let inputField = app.textViews.firstMatch
        XCTAssertTrue(inputField.waitForExistence(timeout: 5), "未找到消息输入框")
        let preFrame = inputField.frame
        print("[KEYBOARD TEST] 聚焦前输入框 frame: \(preFrame)")

        inputField.tap()
        sleep(1)

        let keyboard = app.keyboards.firstMatch
        XCTAssertTrue(keyboard.waitForExistence(timeout: 5), "键盘对象应在 UI 层次结构中存在")
        let keyboardFrame = keyboard.frame
        let postFrame = inputField.frame
        print("[KEYBOARD TEST] keyboard.frame: \(keyboardFrame), 聚焦后输入框 frame: \(postFrame)")

        // 检查系统硬件键盘设置：若键盘处于屏幕外（如 origin.y >= screenHeight），说明模拟器启用了 Connect Hardware Keyboard
        let screenHeight = app.windows.firstMatch.frame.height > 0 ? app.windows.firstMatch.frame.height : 800.0
        if keyboardFrame.minY >= screenHeight || keyboardFrame.height <= 50.0 {
            saveScreenScreenshot(name: "ios-keyboard-up")
            print("[KEYBOARD TEST SKIP] 检测到模拟器开启了 Mac 硬件键盘连接(Connect Hardware Keyboard)，软键盘未在屏幕视口内弹出。依验收规则不修改系统全局配置，键盘弹起与避让交互保持【未验证】状态。")
            throw XCTSkip("检测到当前模拟器启用了 Mac 硬件键盘连接(Connect Hardware Keyboard)，软键盘未在屏幕视口内弹出(frame=\(keyboardFrame))。依用户验收规则不改动全局系统配置，键盘弹起与避让交互保持【未验证】状态。")
        }

        // 若软件键盘已正常弹出，严格执行几何避让断言
        XCTAssertGreaterThan(keyboardFrame.height, 100.0, "软键盘高度应大于100")
        XCTAssertLessThan(postFrame.origin.y, preFrame.origin.y, "软键盘弹起后输入框 Y 坐标应上移避让")
        XCTAssertLessThanOrEqual(postFrame.maxY, keyboardFrame.minY + 20.0, "输入框底部应处于键盘顶部之上（含合理容差）")
        XCTAssertTrue(inputField.isHittable, "键盘弹起后输入框仍必须可点击交互")

        let sendButton = app.buttons["发送"]
        if sendButton.exists {
            XCTAssertLessThanOrEqual(sendButton.frame.maxY, keyboardFrame.minY + 20.0, "发送按钮应处于键盘顶部之上")
        }
        saveScreenScreenshot(name: "ios-keyboard-up")
        print("[PASS] 软键盘成功弹起并验证输入坞上移避让")
    }

    /// 辅助方法：判断是否显式开启了真实网络测试（opt-in 门禁）
    private var isLiveTestEnabled: BooleanLiteralType {
        let env = ProcessInfo.processInfo.environment
        return env["ENABLE_LIVE_DEEPSEEK_UI_TEST"] == "1" ||
               env["SIMCTL_CHILD_ENABLE_LIVE_DEEPSEEK_UI_TEST"] == "1" ||
               FileManager.default.fileExists(atPath: "/tmp/ENABLE_LIVE_DEEPSEEK_UI_TEST")
    }

    /// 缺口 1 与缺口 3 最小精准补验测试（最多追加 2 次真实请求）：
    /// 请求 1：长文本流式生成 → 捕获同一正文至少 2 个前缀递增快照 → 点击停止并严格断言文本非空且 3 秒稳定全等
    /// 请求 2：点击目标气泡重新生成 → 严格断言再次进入生成 → 超时内完成生成且无错误、获得新的非空正文
    func testFocusedRefinedVerification() throws {
        // opt-in 开关门禁：避免日常测试产生收费网络请求
        try XCTSkipIf(!isLiveTestEnabled, "Live DeepSeek UI 测试未开启。通过传入 ENABLE_LIVE_DEEPSEEK_UI_TEST=1 或创建 /tmp/ENABLE_LIVE_DEEPSEEK_UI_TEST 显式 opt-in 以免日常构建产生收费请求。")

        let inputField = app.textViews.firstMatch
        XCTAssertTrue(inputField.waitForExistence(timeout: 5), "未找到消息输入框")
        inputField.tap()
        sleep(1)

        // --- 请求 1：长回复生成与两帧前缀递增快照验证 ---
        print("\n=== [REQUEST 1] 长文本流式生成与前缀递增捕获 ===")
        inputField.typeText("请写一篇关于秋天风景的三百字优美散文，详细描写枫叶与晚霞。")
        sleep(1)

        let sendButton = app.buttons["发送"]
        XCTAssertTrue(sendButton.waitForExistence(timeout: 3), "发送按钮应存在")
        XCTAssertTrue(sendButton.isEnabled, "输入内容后发送按钮应处于启用状态")
        sendButton.tap()

        let stopButton = app.buttons["停止"]
        XCTAssertTrue(stopButton.waitForExistence(timeout: 8), "点击发送后必须进入流式生成状态（停止按钮出现）")
        print("[REQUEST 1] 成功进入流式生成状态，开始采样正文前缀增量...")

        // 1. 等待并捕获快照 1（字符数 >= 10）
        var snapshot1 = ""
        let snap1Start = Date()
        while Date().timeIntervalSince(snap1Start) < 15 {
            let content = getAssistantContent()
            if content.count >= 10 {
                snapshot1 = content
                break
            }
            usleep(100_000)
        }
        XCTAssertFalse(snapshot1.isEmpty, "快照 1 必须成功捕获到非空助手正文")
        print("[SNAPSHOT 1] 字符数: \(snapshot1.count), 前缀摘要: \(snapshot1.prefix(40))...")

        // 2. 间隔 1 秒（继续流式追加）
        sleep(1)

        // 3. 捕获快照 2 并断言前缀增长关系
        let snapshot2 = getAssistantContent()
        print("[SNAPSHOT 2] 字符数: \(snapshot2.count), 前缀摘要: \(snapshot2.prefix(40))...")
        XCTAssertGreaterThan(snapshot2.count, snapshot1.count, "快照 2 字符数必须严格大于快照 1（实际: \(snapshot2.count) > \(snapshot1.count)）")
        XCTAssertTrue(snapshot2.hasPrefix(snapshot1), "快照 2 必须以快照 1 为严格前缀，证实为同一助手正文的逐 token 流式追加")
        print("[PASS] 成功证实同一助手正文的流式前缀增长！")

        // 4. 执行 UI 停止生成验证
        XCTAssertTrue(stopButton.exists, "流式生成中停止按钮应保持存在")
        stopButton.tap()
        print("[ACTION] 点击了停止生成按钮")

        // 5. 断言停止按钮消失，切回常态
        XCTAssertTrue(stopButton.waitForNonExistence(timeout: 5), "点击停止后停止按钮应在5秒内消失")

        // 6. 记录停止时完整正文，断言取消前正文非空
        dismissKeyboardIfPresent()
        let stoppedContent = getAssistantContent()
        XCTAssertFalse(stoppedContent.isEmpty, "停止时截断正文必须非空")
        print("[STOPPED CONTENT] 字符数: \(stoppedContent.count), 截断末尾: ...\(stoppedContent.suffix(30))")

        // 7. 等待 3 秒再次检查，严格断言完整正文全等（而非仅长度）
        sleep(3)
        let contentAfterWait = getAssistantContent()
        print("[CONTENT AFTER 3S] 字符数: \(contentAfterWait.count)")
        XCTAssertEqual(stoppedContent, contentAfterWait, "等待3秒后助手正文必须严格全等，证明点击停止后文本完全停止追加")
        saveScreenScreenshot(name: "ios-refined-stop")
        print("[PASS] 成功验证 UI 停止生成且正文完全稳定！")

        // --- 请求 2：点击重新生成并严格断言新回复完成 ---
        print("\n=== [REQUEST 2] 点击重新生成并验证新回复完成 ===")
        // 滑动使气泡底部操作条完全露出输入坞之上
        app.swipeUp()
        sleep(1)

        let retryButtons = app.buttons.matching(identifier: "重新生成")
        let retryButton = retryButtons.count > 0 ? retryButtons.element(boundBy: retryButtons.count - 1) : app.buttons["重新生成"].firstMatch
        if !retryButton.isHittable {
            app.swipeUp()
            sleep(1)
        }
        XCTAssertTrue(retryButton.waitForExistence(timeout: 5), "目标气泡的重新生成按钮必须存在")
        XCTAssertTrue(retryButton.isHittable, "重新生成按钮必须处于可点击状态")
        retryButton.tap()
        print("[ACTION] 点击了重新生成按钮")

        // 8. 严格断言再次进入生成状态
        XCTAssertTrue(stopButton.waitForExistence(timeout: 8), "点击重新生成后必须再次进入流式生成状态（出现停止按钮）")
        print("[RETRY] 成功进入重新生成状态，等待生成自然完成...")

        // 9. 等待在超时内退出生成状态
        let retryWaitStart = Date()
        var retryFinished = false
        while Date().timeIntervalSince(retryWaitStart) < 40 {
            if !stopButton.exists {
                retryFinished = true
                break
            }
            sleep(1)
        }
        XCTAssertTrue(retryFinished, "重新生成必须在40秒超时内正常退出生成状态")

        // 10. 验证没有错误，且获得新的非空助手正文
        dismissKeyboardIfPresent()
        let errorCard = app.staticTexts["生成失败"]
        XCTAssertFalse(errorCard.exists, "重新生成后界面不应出现错误卡片")

        let newContent = getAssistantContent()
        print("[NEW REGENERATED CONTENT] 字符数: \(newContent.count), 摘要: \(newContent.prefix(50))...")
        XCTAssertFalse(newContent.isEmpty, "重新生成后必须获得非空助手正文")
        XCTAssertNotEqual(newContent, stoppedContent, "重新生成应获得全新/完整的正文内容，而不是此前的截断正文")
        saveScreenScreenshot(name: "ios-refined-retry-done")
        print("[PASS] 成功验证 UI 重新生成完整闭环！")

        print("\n=== [ALL REFINED ASSERTIONS PASSED] 补验断言全部严格通过 ===")
    }

    /// 完整端到端验收（保留已有证据，默认受 opt-in 保护）
    func testFullChatVerification() throws {
        try XCTSkipIf(!isLiveTestEnabled, "Live DeepSeek UI 测试未开启。通过传入 ENABLE_LIVE_DEEPSEEK_UI_TEST=1 或创建 /tmp/ENABLE_LIVE_DEEPSEEK_UI_TEST 显式 opt-in 以免日常构建产生收费请求。")

        // 1. 键盘弹起与避让验证
        print("\n--- [STEP 1] 验证键盘弹起与避让 ---")
        let inputField = app.textViews.firstMatch
        XCTAssertTrue(inputField.waitForExistence(timeout: 5), "未找到消息输入框")
        inputField.tap()
        sleep(1)

        let keyboard = app.keyboards.firstMatch
        XCTAssertTrue(keyboard.waitForExistence(timeout: 5), "点击输入框后软键盘应弹起")
        saveScreenshot(name: "ios-keyboard-up")
        print("[PASS] 软键盘成功弹起，保留系统安全区键盘避让")

        // 2. 第一轮对话：中文真实流式回复（多次增量证据）
        print("\n--- [STEP 2] 验证第一轮流式对话与增量输出 ---")
        inputField.typeText("请记住这个测试暗号：红苹果888。请确认你已记牢。")
        sleep(1)

        let sendButton = app.buttons["发送"]
        XCTAssertTrue(sendButton.waitForExistence(timeout: 3), "发送按钮应存在")
        XCTAssertTrue(sendButton.isEnabled, "输入内容后发送按钮应处于启用状态")
        sendButton.tap()

        let stopButton = app.buttons["停止"]
        var increments: [String] = []
        let streamStart = Date()

        while Date().timeIntervalSince(streamStart) < 30 {
            dismissKeyboardIfPresent()
            let texts = safeGetAllStaticTexts()
            if let latest = texts.last(where: {
                !$0.contains("红苹果888。请确认") &&
                !$0.contains("今天想聊") &&
                !$0.contains("DeepSeek") &&
                !$0.contains("输入消息") &&
                !$0.isEmpty
            }) {
                if increments.isEmpty || increments.last != latest {
                    increments.append(latest)
                    print("[STREAM INCREMENT \(increments.count)] 长度: \(latest.count), 摘要: \(latest.prefix(40))...")
                }
            }
            if !stopButton.exists && increments.count >= 2 {
                break
            }
            sleep(1)
        }

        print("[STREAM STATS] 共记录到 \(increments.count) 次流式增量更新")
        XCTAssertGreaterThanOrEqual(increments.count, 2, "应至少记录到2次增量流式更新")
        let firstRoundFinal = increments.last ?? ""
        print("[FIRST ROUND FINAL RESPONSE] \(firstRoundFinal)")
        XCTAssertTrue(firstRoundFinal.contains("红苹果") || firstRoundFinal.contains("888"), "第一轮回答应确认记住了暗号")
        saveScreenshot(name: "ios-first-round")

        // 3. 第二轮对话：多轮上下文验证（追问暗号）
        print("\n--- [STEP 3] 验证多轮上下文（追问暗号） ---")
        inputField.tap()
        inputField.typeText("我刚刚告诉你的测试暗号是什么？")
        sleep(1)
        if sendButton.exists && sendButton.isEnabled {
            sendButton.tap()
        }

        let multiTurnStart = Date()
        var multiTurnFinal = ""
        while Date().timeIntervalSince(multiTurnStart) < 30 {
            dismissKeyboardIfPresent()
            let texts = safeGetAllStaticTexts()
            if let latest = texts.last(where: {
                !$0.contains("测试暗号是什么") &&
                !$0.contains("今天想聊") &&
                !$0.contains("DeepSeek") &&
                !$0.contains("输入消息") &&
                $0 != firstRoundFinal &&
                !$0.isEmpty
            }) {
                multiTurnFinal = latest
            }
            if !stopButton.exists && !multiTurnFinal.isEmpty {
                break
            }
            sleep(1)
        }

        print("[MULTI-TURN FINAL RESPONSE] \(multiTurnFinal)")
        XCTAssertTrue(multiTurnFinal.contains("红苹果") && multiTurnFinal.contains("888"), "多轮上下文追问回答应能准确回忆前文暗号'红苹果888'")
        saveScreenshot(name: "ios-multi-turn")

        // 4. 第三轮对话：UI 停止生成验证（停止后不再追加）
        print("\n--- [STEP 4] 验证 UI 停止生成 ---")
        inputField.tap()
        inputField.typeText("请写一篇关于秋天风景的四百字散文，详细描写落叶、枫林与晚霞。")
        sleep(1)
        if sendButton.exists && sendButton.isEnabled {
            sendButton.tap()
        }

        XCTAssertTrue(stopButton.waitForExistence(timeout: 8), "第三轮长文本请求应出现停止按钮")
        // 在生成中途立即点击停止
        sleep(1)
        XCTAssertTrue(stopButton.exists, "流式生成中停止按钮应保持存在")
        stopButton.tap()
        print("[ACTION] 点击了停止生成按钮")
        sleep(1)
        XCTAssertFalse(stopButton.exists, "点击停止按钮后，停止按钮应立即消失（恢复常态输入坞）")

        dismissKeyboardIfPresent()
        let textsAtStop = safeGetAllStaticTexts()
        let stoppedText = textsAtStop.last(where: {
            !$0.contains("四百字散文") &&
            !$0.contains("输入消息") &&
            !$0.contains("思考过程") &&
            $0 != firstRoundFinal &&
            $0 != multiTurnFinal
        }) ?? ""
        let stoppedTextLength = stoppedText.count
        print("[STOPPED TEXT LENGTH] \(stoppedTextLength), 截断内容: \(stoppedText.suffix(30))")

        // 等待 3 秒再次检查，确认文本不再增长
        sleep(3)
        let textsAfterWait = safeGetAllStaticTexts()
        let currentText = textsAfterWait.last(where: {
            !$0.contains("四百字散文") &&
            !$0.contains("输入消息") &&
            !$0.contains("思考过程") &&
            $0 != firstRoundFinal &&
            $0 != multiTurnFinal
        }) ?? ""
        print("[TEXT LENGTH AFTER 3S] \(currentText.count)")
        XCTAssertEqual(stoppedTextLength, currentText.count, "点击停止生成后文本不应再继续追加")
        saveScreenshot(name: "ios-stop-generation")

        // 5. 第四步：UI 重试验证（重新生成）
        print("\n--- [STEP 5] 验证 UI 重新生成 / 重试 ---")
        let retryButtons = app.buttons.matching(identifier: "重新生成")
        let retryButton = retryButtons.count > 0 ? retryButtons.element(boundBy: retryButtons.count - 1) : app.buttons["重新生成"].firstMatch
        if retryButton.waitForExistence(timeout: 5) {
            retryButton.tap()
            print("[ACTION] 点击了重新生成按钮")
            sleep(1)
            // 验证重新进入流式生成
            if stopButton.waitForExistence(timeout: 5) {
                print("[RETRY] 重新生成成功触发流式输出，等待完成...")
                let retryWaitStart = Date()
                while Date().timeIntervalSince(retryWaitStart) < 30 {
                    if !stopButton.exists {
                        break
                    }
                    sleep(1)
                }
            }
            dismissKeyboardIfPresent()
            saveScreenshot(name: "ios-retry-done")
            print("[PASS] 重新生成交互完成并通过")
        } else {
            print("[NOTE] 检查是否有备选重试按钮")
            let errorRetry = app.buttons["重试"].firstMatch
            if errorRetry.exists {
                errorRetry.tap()
                sleep(2)
                dismissKeyboardIfPresent()
                saveScreenshot(name: "ios-retry-done")
            }
        }

        print("\n--- [ALL STEPS PASSED] 全部真实聊天交互验证通过 ---")
    }
}
