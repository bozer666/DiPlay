# DiPlay 中文化 · 术语表与第一批对照清单（待复核）

本文件是实施前的复核底稿：**第 2 节术语表和第 6 节争议项需要你确认**，确认后我才动代码。
第一批范围：主页 / 设置页 / 关于页 / 全部弹窗与 toast / 前台服务通知 / `shared` 模块里被第一批用到的字符串。

---

## 1. 已锁定的语言机制（四轮问询结论）

| 项 | 决定 |
|---|---|
| 默认行为 | 不写偏好 = 不干预系统。中文系统自动中文，其它语言回退英文 |
| 资源目录 | `values/`（英文，默认）+ `values-zh/`（简体）。**不用 `values-zh-rCN`**，所以 zh-TW / zh-HK 车机也显示简体 |
| 显式选择 | 设置页两档：**简体中文 / English**。选过即锁定，永不跟随系统 |
| 入口 | 仅设置页新增一项，放在「关于与诊断」之前 |
| 生效 | 选完立即重建当前设置页；投影中的 CarPlay 界面保持原语言，下次连接生效 |
| 机制 | 全版本自建 `attachBaseContext` 包 `Configuration`（不引 appcompat、不改主题、不用系统「应用语言」入口） |
| 不翻 | 诊断报告、会话日志、Logcat、内部状态串、通知渠道名、正在显示的通知 |
| 专名 | DiPlay / CarPlay / Wi-Fi Direct / HEVC / HUD / iPhone 保留英文 |
| 字体 | 不做兜底，仅在文档标注「依赖车机自带 CJK 字体」 |
| 验收 | `:mobile:lintDebug` 零 `MissingTranslation` + `:common:testDebugUnitTest` + `:mobile:assembleDebug` 通过，车机目视由你完成 |

---

## 2. 术语表（Glossary）— 需确认

| 英文 | 中文 | 理由 / 备注 |
|---|---|---|
| head unit | 车机 | 车机用户最熟悉的说法；不用「主机」「车载单元」 |
| car settings | 车机设置 | 指车机自带的设置 App，不是 DiPlay 的设置 |
| car hotspot | 车机热点 | 与「Wi-Fi Direct」并列时能区分两种联网方式 |
| car home | 车机主页 | 对应跳回桌面（`Intent.CATEGORY_HOME`） |
| instrument cluster | 仪表盘 | 与 HUD 并列出现 |
| HUD | HUD | 保留缩写；首次出现处若空间允许用「抬头显示（HUD）」 |
| Nearby devices | 附近的设备 | 采用 Android 官方中文译名 |
| Location | 位置信息 | 采用 Android 官方中文译名 |
| Microphone | 麦克风 | — |
| Wi-Fi Direct | Wi-Fi Direct | 保留；不译「无线直连」 |
| Wireless link | 无线连接方式 | 指 Wi-Fi Direct / 车机热点二选一，不是单个连接 |
| Resolution · Native | 分辨率 · 原生 | 指车机原生分辨率 |
| Music buffer | 音乐缓冲 | — |
| Frame rate | 帧率 | — |
| Efficient video | 高效视频 | 说明文案里保留 HEVC |
| Right-hand drive | 右舵车 | — |
| Full screen | 全屏 | — |
| Pair / Paired device | 配对 / 已配对设备 | — |
| phone projection app | 手机投屏应用 | 指其它 CarPlay/Android Auto 类应用 |
| diagnostic report | 诊断报告 | 报告内容本身保持英文 |
| public preview | 公开预览 | 与版本号并列显示 |
| tagline（品牌标语） | 中文化 | 见第 6 节 A1，语气需你定 |

---

## 3. 绝对不翻清单（改动时必须逐条守住）

| 位置 | 内容 | 原因 |
|---|---|---|
| [DiPlayActivity.kt:540-561](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L540-L561) | 诊断报告正文与字段名 | 你已决定「诊断全英文」；远程排查依赖固定字段 |
| [DiPlayActivity.kt:519](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L519) | `DiPlay-yyyyMMdd-HHmmss-SSS.txt` 文件名 | 支持流程与用户沟通依赖固定格式 |
| [CarPlayHostActivity.kt:3323-3339](../common/src/main/java/com/shilapi/xcertplay/CarPlayHostActivity.kt#L3323-L3339) | `friendlyStage()` 的**左值**匹配键（`"Turn on Wi-Fi"`、`"needs a reset"`…） | 一旦翻译，状态文案映射全部失效 |
| [CarPlayHostActivity.kt:3422-3425](../common/src/main/java/com/shilapi/xcertplay/CarPlayHostActivity.kt#L3422-L3425) | `CarPlayStatus` → 英文字符串（`"Discovering iPhone"`…） | 既是日志内容，又是 `friendlyStage` 的匹配键 |
| [CarPlayHostActivity.kt:3341-3351](../common/src/main/java/com/shilapi/xcertplay/CarPlayHostActivity.kt#L3341-L3351) | 会话日志写入与时间戳格式 | 日志全英文 |
| [DisplayDiagnosticSnapshot.kt:43-45](../common/src/main/java/com/shilapi/xcertplay/DisplayDiagnosticSnapshot.kt#L43-L45) | `"Phone display peer "`、`"Video: decoder="` 等前缀匹配 | 报告分组依赖前缀 |
| [DiagnosticRedactor.kt:12](../common/src/main/java/com/shilapi/xcertplay/DiagnosticRedactor.kt#L12) | `"TRACE "`、`"PHONE "` 脱敏标记 | 隐私脱敏规则 |
| [WifiP2pGroupManager.kt:515](../shared/src/main/java/com/shilapi/xcertplay/network/WifiP2pGroupManager.kt#L515)、[P2pOwnership.kt:20](../shared/src/main/java/com/shilapi/xcertplay/network/P2pOwnership.kt#L20) 等 | `shared` 抛出的内部异常文案 | 是 `friendlyStage` 的输入，不是最终用户文案 |
| intent extra、`page` 键（`"home"`/`"settings"`/`"about"`/`"wireless-recovery"`）、SharedPreferences 键、`ACTION_STOP` | 控制流字符串 | 与翻译无关，禁止顺手改 |

**同时必须处理的三个冲突**（不改就会自相矛盾）：

1. `CarPlaySize.label`（`Large`/`Medium`/`Small`）同时喂 UI（[DiPlayActivity.kt:385](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L385)）和英文报告（[DiPlayActivity.kt:546](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L546)）→ 拆成「稳定英文值」+「UI 文案」。
2. `ManualHotspotValidation`（[ManualHotspotValidation.kt:10-16](../shared/src/main/java/com/shilapi/xcertplay/orchestration/ManualHotspotValidation.kt#L10-L16)）直接返回英文字面消息 → 改成返回错误码，由 `common` 映射资源文案。
3. `DiPlayPreferences.phoneName` 的默认值 `"Your iPhone"`（[DiPlayBootstrap.kt:54](../common/src/main/java/com/shilapi/xcertplay/DiPlayBootstrap.kt#L54)）会在设置项标题里显示 → 走资源。

---

## 4. 第一批中英对照清单

> 位置列指向**当前代码行号**，实施时键名可微调。
> 标 ⚠ 的条目属于第 6 节待确认项。

### 4.1 主页 `home()`

| 资源键 | 英文原文（位置） | 中文译文 | 备注 |
|---|---|---|---|
| `home_action_car_home` | Car home（[L117](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L117)） | 车机主页 | ⚠ A2；按钮宽 130dp，4 字安全 |
| `action_back` | Back（[L117](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L117)） | 返回 | 共用 |
| `home_tagline` | YOUR PHONE. YOUR DRIVE.（[L136](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L136)） | 你的手机，你的旅途 | ⚠ A1；字号 12 + `letterSpacing .16f`，中文会显得字距很开，需目视 |
| `home_headline` | A familiar drive.（[L137](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L137)） | 熟悉的驾驶感 | ⚠ A1 |
| `home_body` | Your maps, music and conversations.\nCarPlay, right here on your car display.（[L138](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L138)） | 你的地图、音乐与通话。\nCarPlay，就在你的车机屏幕上。 | 保留换行位置 |
| `home_card_label` | WIRELESS CARPLAY（[L140](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L140)） | 无线 CarPlay | 大写小标题，中文不套大写 |
| `status_ready` | Ready when you are（[L141](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L141)、[L509](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L509)） | 随时可以开始 | 状态文案，24px 加粗 |
| `action_connect_phone` | Connect phone（[L143](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L143)、[L512](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L512)） | 连接手机 | 主按钮 |
| `home_pair_hint` | Pair your iPhone with the car’s Bluetooth, then connect.\nKeep Bluetooth and Wi-Fi on.（[L148](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L148)） | 先把 iPhone 与车机蓝牙配对，再发起连接。\n请保持蓝牙和 Wi-Fi 开启。 | — |
| `home_hotspot_off` | The car hotspot “%1$s” is off. Turn it on in the car settings before connecting.（[L150](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L150)） | 车机热点「%1$s」未开启。请先在车机设置里打开，再发起连接。 | 含 SSID 占位符 |
| `action_open_car_hotspot_settings` | Open car hotspot settings（[L151](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L151)） | 打开车机热点设置 | — |
| `action_choose_iphone` | Choose iPhone（[L153](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L153)） | 选择 iPhone | — |
| `action_disconnect` | Disconnect（[L154](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L154)） | 断开连接 | 通知里也有同名按钮 |
| `content_desc_carplay` | CarPlay / CarPlay icon（[L115](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L115)、[L162](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L162)） | CarPlay / CarPlay 图标 | 无障碍描述 |
| `action_connect_usb` | Connect with USB（[L169](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L169)） | 使用 USB 连接 | — |
| `home_usb_hint` | Plug your iPhone into a USB data port.\nAllow CarPlay when your iPhone asks.（[L170](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L170)） | 将 iPhone 接入 USB 数据口。\niPhone 询问时请选择允许 CarPlay。 | — |
| `action_settings` | Settings（[L171](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L171)） | 设置 | — |
| `home_settings_hint` | Make DiPlay feel right for your car.（[L172](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L172)） | 让 DiPlay 更贴合你的车。 | — |
| `home_preview_version` | PUBLIC PREVIEW  ·  %1$s（[L173](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L173)） | 公开预览  ·  %1$s | ⚠ A3；两个空格是排版，需保留 |
| `status_open_carplay` | Open CarPlay（[L512](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L512)） | 打开 CarPlay | — |
| `status_carplay_connected` | CarPlay connected（[L506](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L506)） | CarPlay 已连接 | — |
| `status_connecting` | Connecting to your iPhone…（[L507](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L507)） | 正在连接你的 iPhone… | — |
| `status_ready_for` | Ready for %1$s（[L508](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L508)） | 已就绪：%1$s | 占位符是手机名 |
| `status_setup_attention` | Setup needs attention（[L505](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L505)） | 设置需要处理 | — |
| `setup_error` | Local setup could not finish. Reinstall the complete DiPlay beta APK and try again.（[L75](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L75)） | 本地初始化未完成。请重新安装完整的 DiPlay 测试版 APK 后再试。 | — |

### 4.2 设置页 `settings()`

| 资源键 | 英文原文（位置） | 中文译文 | 备注 |
|---|---|---|---|
| `settings_headline` | Your drive, your way.（[L200](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L200)） | 你的车，你做主 | ⚠ A1；34px 加粗 |
| `settings_body` | Apply reconnects CarPlay for size, resolution, music buffer and frame rate. Other changes apply to your next connection.（[L201](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L201)） | 点「应用并重新连接」会对尺寸、分辨率、音乐缓冲和帧率生效；其它改动在下次连接时生效。 | — |
| `section_auto_connect` | Automatic connection（[L202](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L202)） | 自动连接 | 分组标题 |
| `toggle_connect_on_open` | Connect when DiPlay opens（[L203](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L203)） | 打开 DiPlay 时自动连接 | — |
| `toggle_connect_on_open_desc` | Use your last connection type and selected iPhone.（[L203](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L203)） | 使用上次的连接方式和已选择的 iPhone。 | — |
| `toggle_autostart_boot` | Open after the car starts（[L204](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L204)） | 车机启动后自动打开 | — |
| `toggle_autostart_boot_desc` | Availability depends on your head unit’s startup settings.（[L204](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L204)） | 能否生效取决于车机的开机启动设置。 | — |
| `action_choose_iphone_named` | Choose iPhone · %1$s（[L205](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L205)） | 选择 iPhone · %1$s | 手机名来自偏好 |
| `section_wireless_connection` | Wireless connection（[L207](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L207)） | 无线连接 | — |
| `section_display_performance` | Display and performance（[L208](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L208)） | 显示与性能 | — |
| `choice_carplay_size` | CarPlay size（[L385](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L385)） | CarPlay 尺寸 | 选项见 4.6 |
| `choice_carplay_size_note` | Changes the size of CarPlay icons and text. Applying a size reconnects CarPlay.（[L388](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L388)） | 改变 CarPlay 图标和文字的大小。应用后需要重新连接 CarPlay。 | — |
| `choice_resolution` | Resolution（[L210](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L210)） | 分辨率 | — |
| `choice_resolution_native` | Native（[L210](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L210)） | 原生 | — |
| `choice_resolution_80` | 80% · lighter load（[L210](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L210)） | 80% · 负载更轻 | 保留 `·` 分隔符 |
| `choice_resolution_60` | 60% · lightest load（[L210](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L210)） | 60% · 负载最轻 | — |
| `choice_music_buffer` | Music buffer（[L212](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L212)） | 音乐缓冲 | — |
| `choice_music_buffer_300` | 300 ms · default（[L212](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L212)） | 300 ms · 默认 | 单位不译 |
| `choice_music_buffer_500` | 500 ms（[L212](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L212)） | 500 ms | — |
| `choice_music_buffer_1000` | 1000 ms · most stable（[L212](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L212)） | 1000 ms · 最稳定 | — |
| `choice_frame_rate` | Frame rate（[L216](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L216)） | 帧率 | — |
| `choice_frame_rate_30` | 30 fps · lighter load（[L216](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L216)） | 30 fps · 负载更轻 | — |
| `choice_frame_rate_60` | 60 fps · smoother motion（[L216](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L216)） | 60 fps · 画面更流畅 | — |
| `toggle_efficient_video` | Efficient video（[L217](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L217)） | 高效视频 | — |
| `toggle_efficient_video_desc` | Use HEVC. Leave off for the widest head-unit compatibility.（[L217](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L217)） | 使用 HEVC。关闭可获得最广泛的车机兼容性。 | — |
| `toggle_right_hand_drive` | Right-hand drive（[L218](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L218)） | 右舵车 | — |
| `toggle_right_hand_drive_desc` | Place CarPlay’s controls closer to the driver.（[L218](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L218)） | 让 CarPlay 的操作控件更靠近驾驶位。 | — |
| `toggle_full_screen` | Full screen（[L219](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L219)） | 全屏 | — |
| `toggle_full_screen_desc` | Hide the car’s system bars while CarPlay is open.（[L219](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L219)） | CarPlay 打开时隐藏车机的系统栏。 | — |
| `section_byd_navigation` | BYD navigation（[L223](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L223)） | BYD 导航 | 仅 BYD 车机显示 |
| `toggle_byd_hud` | Navigation on HUD and instrument cluster（[L224](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L224)） | 在 HUD 和仪表盘上显示导航 | ⚠ A4 |
| `toggle_byd_hud_desc` | Show phone navigation arrows, distance and street names on supported BYD displays. Vehicle compatibility varies.（[L225](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L225)） | 在支持的 BYD 显示屏上显示手机导航的转向箭头、距离和道路名称。不同车型支持情况不同。 | ⚠ A4 |
| `section_permissions_help` | Permissions and connection help（[L228](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L228)） | 权限与连接帮助 | — |
| `permissions_body` | Nearby devices connects your iPhone. Microphone enables Siri and calls. Older Android versions also require Location for wireless setup. USB mode may ask for a local VPN connection.（[L229](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L229)） | 「附近的设备」用于连接 iPhone，「麦克风」用于 Siri 和通话。较旧的 Android 版本还需要「位置信息」才能完成无线连接。USB 模式可能请求建立本地 VPN 连接。 | 权限名用官方译名 |
| `action_app_permissions` | App permissions（[L230](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L230)） | 应用权限 | — |
| `action_bluetooth_settings` | Bluetooth settings（[L231](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L231)） | 蓝牙设置 | — |
| `action_wireless_help` | Wireless connection help（[L232](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L232)） | 无线连接帮助 | — |
| `section_about_diagnostics` | About and diagnostics（[L234](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L234)） | 关于与诊断 | — |
| `action_about_diplay` | About DiPlay（[L235](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L235)） | 关于 DiPlay | — |
| `action_save_report` | Save diagnostic report（[L236](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L236)、[L570](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L570)） | 保存诊断报告 | — |
| `action_saving_report` | Saving report…（[L236](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L236)、[L534](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L534)） | 正在保存报告… | — |
| `report_destination_downloads` | Reports save to Downloads/DiPlay. （[L241](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L241)） | 报告保存到 Downloads/DiPlay。 | 路径不译 |
| `report_destination_choose` | Choose where to save your report. （[L241](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L241)） | 选择报告的保存位置。 | 仅 Android 10 以下 |
| `report_privacy_note` | Nothing is sent automatically. Protocol payloads and credentials are excluded.（[L242](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L242)） | 不会自动上传任何内容。协议报文和凭据已被排除。 | — |
| `section_language` | （新增） | 语言 | ⚠ A5：中英并排显示为「语言 / Language」还是只显示中文？ |
| `choice_language` | （新增） | 语言 | 两档：简体中文 / English |

### 4.3 关于页 `about()`

| 资源键 | 英文原文（位置） | 中文译文 | 备注 |
|---|---|---|---|
| `about_title` | DiPlay（[L247](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L247)） | DiPlay | 专名不译 |
| `about_tagline` | CarPlay, at home in your car.（[L248](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L248)） | CarPlay，在你的车里安家。 | ⚠ A1 |
| `about_preview` | Public preview · %1$s（[L249](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L249)） | 公开预览 · %1$s | ⚠ A3 |
| `about_body` | An independent CarPlay receiver for Android head units. Wired and wireless connections run on the head unit, with local authentication. A standard iPhone can connect without a jailbreak, Mac, dongle or sign-in.\n\nThis preview uses an experimental accessory identity. Compatibility with every iPhone and head unit is still being tested. It is not an Apple-certified product.（[L250](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L250)） | 面向 Android 车机的独立 CarPlay 接收端。有线与无线连接都在车机上运行，并在本地完成认证。标准 iPhone 无需越狱、Mac、转接器或登录即可连接。\n\n本预览版使用实验性的配件身份。与所有 iPhone、车机的兼容性仍在测试中。本产品未获得 Apple 认证。 | 保留段落结构 |
| `about_oss_section` | Made possible by open source（[L252](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L252)） | 开源成就 | ⚠ A6：措辞可改 |
| `about_oss_body` | Receiver based on xcertplay, licensed under GPL-3.0. DiPlay’s interface follows DiAuto’s design, licensed under AGPL-3.0.\n\nIncludes AndroidX, Bouncy Castle, JmDNS and SLF4J. Source and license notices accompany this release.\n\nCarPlay and the CarPlay icon belong to Apple Inc. DiPlay is an independent project.（[L253](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L253)） | 接收端基于 xcertplay，采用 GPL-3.0 许可。DiPlay 的界面沿用 DiAuto 的设计，采用 AGPL-3.0 许可。\n\n包含 AndroidX、Bouncy Castle、JmDNS 和 SLF4J。源码与许可声明随本次发行一并提供。\n\nCarPlay 及 CarPlay 图标归 Apple Inc. 所有。DiPlay 是独立项目。 | 许可证名与组件名不译 |

### 4.4 弹窗 / toast / 权限帮助

| 资源键 | 英文原文（位置） | 中文译文 |
|---|---|---|
| `dialog_hotspot_off_title` | Car hotspot is off（[L263](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L263)） | 车机热点未开启 |
| `dialog_hotspot_off_body` | DiPlay connects through the car hotspot “%1$s”. Turn it on in the car settings, then connect.（[L264](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L264)） | DiPlay 通过车机热点「%1$s」连接。请先在车机设置里打开，再发起连接。 |
| `action_open_car_settings` | Open car settings（[L265](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L265)） | 打开车机设置 |
| `action_connect` | Connect（[L266](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L266)） | 连接 |
| `action_cancel` | Cancel（[L267](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L267) 等多处） | 取消 |
| `wireless_link_wifi_direct` | Wi-Fi Direct · default（[L291](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L291)） | Wi-Fi Direct · 默认 |
| `wireless_link_car_hotspot` | Car hotspot（[L291](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L291)） | 车机热点 |
| `action_wireless_link_named` | Wireless link · %1$s（[L292](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L292)） | 无线连接方式 · %1$s |
| `dialog_wireless_link_title` | Wireless link（[L295](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L295)） | 无线连接方式 |
| `action_apply_reconnect` | Apply and reconnect（[L297](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L297)、[L610](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L610)） | 应用并重新连接 |
| `action_save` | Save（[L297](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L297)、[L378](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L378)、[L610](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L610)） | 保存 |
| `wireless_p2p_note` | DiPlay creates its own Wi-Fi Direct network for the iPhone.（[L311](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L311)） | DiPlay 会为 iPhone 自建一个 Wi-Fi Direct 网络。 |
| `action_hotspot_name_named` | Hotspot name · %1$s（[L318](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L318)） | 热点名称 · %1$s |
| `dialog_hotspot_name_title` | Car hotspot name（[L319](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L319)、[L353](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L353)） | 车机热点名称 |
| `action_hotspot_password_named` | Hotspot password · %1$s（[L326](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L326)） | 热点密码 · %1$s |
| `value_none` | none（[L326](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L326)） | 未设置 |
| `dialog_hotspot_password_title` | Car hotspot password（[L327](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L327)、[L354](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L354)） | 车机热点密码 |
| `hotspot_manual_note` | Turn on the hotspot in the car settings first and enter the same name and password here. The iPhone joins this network for CarPlay. Changes apply to your next connection.（[L333](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L333)） | 请先在车机设置里打开热点，并在此填入相同的名称和密码。iPhone 会加入该网络以使用 CarPlay。改动在下次连接时生效。 |
| `dialog_bluetooth_off_title` | Turn on Bluetooth（[L422](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L422)） | 请开启蓝牙 |
| `dialog_bluetooth_off_body` | Enable the car’s Bluetooth and pair your iPhone first.（[L423](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L423)） | 请先开启车机蓝牙并配对 iPhone。 |
| `action_open_bluetooth` | Open Bluetooth（[L424](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L424)、[L431](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L431)） | 打开蓝牙 |
| `action_later` | Later（[L425](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L425)、[L587](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L587)） | 稍后 |
| `dialog_pair_iphone_title` | Pair your iPhone（[L429](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L429)） | 配对 iPhone |
| `dialog_pair_iphone_body` | On your iPhone, open Settings → Bluetooth and pair with the car. Then return to DiPlay and choose Connect phone.（[L430](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L430)） | 在 iPhone 上打开「设置 → 蓝牙」与车机配对，然后回到 DiPlay 选择「连接手机」。 |
| `action_got_it` | Got it（[L432](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L432)、[L451](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L451)） | 知道了 |
| `dialog_choose_iphone_title` | Choose your iPhone（[L434](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L434)） | 选择你的 iPhone |
| `paired_device_fallback` | Paired device（[L436](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L436)） | 已配对设备 |
| `action_pair_another` | Pair another（[L444](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L444)） | 配对其它手机 |
| `dialog_wireless_help_title` | Wireless connection help（[L449](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L449)） | 无线连接帮助 |
| `dialog_wireless_help_body` | Pair your iPhone with the car’s Bluetooth, keep Wi-Fi on, and allow CarPlay on the iPhone. Close any other phone-projection app.\n\nIf a previous projection app left its connection running, reset CarPlay Wi-Fi below and connect again. Your car’s normal internet Wi-Fi stays on.（[L450](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L450)） | 将 iPhone 与车机蓝牙配对，保持 Wi-Fi 开启，并在 iPhone 上允许 CarPlay。请关闭其它手机投屏应用。\n\n如果此前的投屏应用留下了未结束的连接，请先点下方「重置 CarPlay Wi-Fi」再重新连接。车机原本的上网 Wi-Fi 不受影响。 |
| `action_reset_carplay_wifi` | Reset CarPlay Wi-Fi（[L452](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L452)） | 重置 CarPlay Wi-Fi |
| `dialog_reset_title` | Reset CarPlay Wi-Fi?（[L464](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L464)） | 重置 CarPlay Wi-Fi？ |
| `dialog_reset_body` | This ends the existing Wi-Fi Direct connection, including one left behind after reinstalling. Close other projection apps first. Your car’s internet Wi-Fi stays on.（[L465](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L465)） | 这会结束当前已有的 Wi-Fi Direct 连接，包括重装后残留的连接。请先关闭其它投屏应用。车机原本的上网 Wi-Fi 不受影响。 |
| `action_reset_and_connect` | Reset and connect（[L466](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L466)） | 重置并连接 |
| `toast_no_wifi_direct` | This head unit does not support Wi-Fi Direct.（[L473](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L473)） | 该车机不支持 Wi-Fi Direct。 |
| `toast_wifi_direct_busy` | Wi-Fi Direct is still busy. Close the other projection app and try again.（[L486](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L486)） | Wi-Fi Direct 仍被占用。请关闭其它投屏应用后重试。 |
| `toast_wifi_reset_failed` | Could not reset Wi-Fi Direct. Close the other projection app and try again.（[L494](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L494)） | 无法重置 Wi-Fi Direct。请关闭其它投屏应用后重试。 |
| `permission_help_wireless_title` | Wireless permissions（[L498](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L498)） | 无线连接权限 |
| `permission_help_wireless_body` | Allow Nearby devices and, on older Android versions, Location before resetting CarPlay Wi-Fi.（[L498](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L498)） | 请先允许「附近的设备」权限；较旧的 Android 版本还需要允许「位置信息」，才能重置 CarPlay Wi-Fi。 |
| `permission_help_nearby_title` | Nearby devices（[L59](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L59)） | 附近的设备 |
| `permission_help_nearby_body` | Allow Nearby devices so DiPlay can connect to your paired iPhone.（[L59](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L59)） | 请允许「附近的设备」权限，DiPlay 才能连接已配对的 iPhone。 |
| `action_app_settings` | App settings（[L585](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L585)） | 应用设置 |
| `toast_open_from_car_settings` | Open this setting from your car’s Settings app.（[L589](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L589)） | 请在车机的设置应用中打开该项。 |
| `toast_save_location_unavailable` | This head unit could not open a save location. Please try saving to Downloads again.（[L526](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L526)） | 该车机无法打开保存位置。请重试保存到 Downloads。 |
| `toast_no_file_picker` | This head unit has no available file picker to save the report.（[L527](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L527)） | 该车机没有可用的文件选择器，无法保存报告。 |
| `dialog_report_saved_title` | Diagnostic report saved（[L572](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L572)） | 诊断报告已保存 |
| `report_saved_other` | Your report was saved to the selected location.（[L573](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L573)） | 报告已保存到你选择的位置。 |
| `action_done` | Done（[L574](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L574)） | 完成 |
| `dialog_report_failed_title` | Could not save the report（[L576](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L576)） | 无法保存报告 |
| `dialog_report_failed_body` | Check that storage is available, or choose another save location.（[L577](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L577)） | 请检查存储是否可用，或选择其它保存位置。 |
| `action_choose_location` | Choose location（[L578](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L578)） | 选择位置 |
| `action_close` | Close（[L579](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L579)） | 关闭 |

### 4.5 前台服务通知 `DiPlaySessionService`

| 资源键 | 英文原文（位置） | 中文译文 | 备注 |
|---|---|---|---|
| `notification_channel_connection` | CarPlay connection（[L26](../common/src/main/java/com/shilapi/xcertplay/DiPlaySessionService.kt#L26)） | CarPlay 连接 | **渠道名只在首次创建时生效**，切语言不会更新（你已接受） |
| `notification_title` | DiPlay（[L31](../common/src/main/java/com/shilapi/xcertplay/DiPlaySessionService.kt#L31)） | DiPlay | 专名 |
| `notification_text_running` | CarPlay connection running（[L32](../common/src/main/java/com/shilapi/xcertplay/DiPlaySessionService.kt#L32)） | CarPlay 连接运行中 | 会话期间不刷新，下次会话生效 |
| `action_disconnect` | Disconnect（[L34](../common/src/main/java/com/shilapi/xcertplay/DiPlaySessionService.kt#L34)） | 断开连接 | 与主页按钮共用 |

### 4.6 `shared` 模块（双身份标签 + 校验消息）

**CarPlay 尺寸**（[CarPlaySize.kt:7-10](../shared/src/main/java/com/shilapi/xcertplay/airplay/CarPlaySize.kt#L7-L10)）

| 英文稳定值（保留，用于诊断报告） | UI 文案键 | 中文译文 |
|---|---|---|
| `Large` | `carplay_size_large` | 大 |
| `Medium` | `carplay_size_medium` | 中 |
| `Small` | `carplay_size_small` | 小 |

**车机热点校验**（[ManualHotspotValidation.kt:10-16](../shared/src/main/java/com/shilapi/xcertplay/orchestration/ManualHotspotValidation.kt#L10-L16)）→ 改为返回错误码，由 `common` 映射：

| 错误码 | 英文原文 | 中文译文 |
|---|---|---|
| `EMPTY_SSID` | Enter the car hotspot name | 请输入车机热点名称 |
| `SSID_TOO_LONG` | The hotspot name must be at most 32 bytes | 热点名称最长 32 字节 |
| `INVALID_CHARACTER` | The name or password contains an invalid character | 名称或密码包含无效字符 |
| `PASSWORD_LENGTH` | The hotspot password must be 8–63 characters | 热点密码需为 8–63 个字符 |

### 4.7 偏好默认值

| 资源键 | 英文原文（位置） | 中文译文 | 备注 |
|---|---|---|---|
| `phone_default_name` | Your iPhone（[DiPlayBootstrap.kt:54](../common/src/main/java/com/shilapi/xcertplay/DiPlayBootstrap.kt#L54)） | 你的 iPhone | 显示在「选择 iPhone · %1$s」里 |
| `phone_fallback_name` | iPhone（[DiPlayActivity.kt:440](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L440)） | iPhone | **保留英文**：这是会被存入偏好的设备名，不是界面文案 |

---

## 5. 范围修正（读代码后对第一批的调整）

| 文件 | 处理 | 原因 |
|---|---|---|
| [ImageCropActivity.kt](../common/src/main/java/com/shilapi/xcertplay/ImageCropActivity.kt) | **移入第二批** | 只从会话内菜单进入（[CarPlayHostActivity.kt:216](../common/src/main/java/com/shilapi/xcertplay/CarPlayHostActivity.kt#L216) 的 AirPlay 图标选择），不属于「主界面」。共 8 条：`Loading image` / `Cancel` / `Save 1:1` / `Could not decode image` / `Drag to move, pinch to zoom` / `Image is not ready` / `Could not encode image` / `Could not save image` |
| [SafeAreaEditorView.kt](../common/src/main/java/com/shilapi/xcertplay/SafeAreaEditorView.kt#L109-L112) | **无需翻译** | 它只绘制 `x=123` / `y=456` 这类数值坐标，没有自然语言文案 |
| [MainActivity.kt](../common/src/main/java/com/shilapi/xcertplay/MainActivity.kt) | **暂不翻译** | I2C/MFi 板级诊断界面，全仓库无任何入口启动它（`exported=false` 且无 intent-filter），用户不可达。你若要保留这条通道我再翻 |
| [StandaloneHudDemoActivity.kt](../mobile/src/debug/java/com/shilapi/xcertplay/hud/StandaloneHudDemoActivity.kt) | **暂不翻译** | 仅 debug 变体的 HUD 演示界面 |
| [MyCarAppScreen.kt](../shared/src/main/java/com/shilapi/xcertplay/shared/MyCarAppScreen.kt#L11-L13) | **本次不翻译（已记录，待你定）** | AAOS 模板应用界面，2 条文案：`xcertplay hardware status`（标题）与 `Hardware transport is not configured. Board I2C needs a /dev/i2c-N path and OS/SELinux permission; CH341 needs deployed VID/PID configuration.`。**仅在 `automotive` 变体可达**：[mobile 的 AndroidManifest.xml](../mobile/src/main/AndroidManifest.xml) 用 `tools:node="remove"` 移除了 `MyCarAppService`，而 `releases/` 里只有 `DiPlay-0.2.0.apk`（mobile 产物）。补充约束：`shared` 不能依赖 `common`，所以这里既拿不到 `R`，也拿不到 `AppLanguage`——若将来真要发行 automotive 变体，需要给它自己的 `values-zh`，并且它只能跟随系统语言，无法跟随应用内显式选择 |

---

## 6. 待你确认的争议项

| 编号 | 问题 | 我的建议 |
|---|---|---|
| **A1** | 四句品牌标语（`YOUR PHONE. YOUR DRIVE.` / `A familiar drive.` / `Your drive, your way.` / `CarPlay, at home in your car.`）的语气。这是最容易返工的部分 | 采用「你的手机，你的旅途」/「熟悉的驾驶感」/「你的车，你做主」/「CarPlay，在你的车里安家。」偏简洁、不煽情 |
| **A2** | `Car home` 按钮（跳回车机桌面）译法 | 「车机主页」；若你觉得歧义可改「返回车机桌面」（6 字，130dp 宽度仍安全） |
| **A3** | `PUBLIC PREVIEW` / `Public preview` 是否中文化 | 建议中文化为「公开预览」，保留 `·` 与版本号 |
| **A4** | HUD 相关：`HUD` 是否始终保留英文缩写 | 建议保留 `HUD`；若目标用户不熟，可改「抬头显示」 |
| **A5** | 新增语言项所在分组的标题 | 建议「语言 / Language」中英并排——因为用户看不懂当前语言时，双语标题是唯一的自救线索 |
| **A6** | `Made possible by open source` 的译法 | 「开源成就」偏简短；也可用「感谢开源」或「基于开源项目」 |
| **A7** | 中文标点风格 | 建议：句末不加句号（车机 UI 通行做法），分句用中文逗号；引号统一用「」；比例/单位保留半角与 `·` 分隔符 |
| **A8** | 中文排版：主页标语带 `letterSpacing = .16f`（[DiPlayActivity.kt:136](../common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt#L136)），中文加字距会显得松散 | 建议中文标语把 `letterSpacing` 降到 `0.04f` 左右；若你希望完全不动代码，我就保持原值，车机上你自己看效果再定 |
| **A9** | `Efficient video` 译名 | 「高效视频」；若你觉得生硬可改「高效编码（HEVC）」 |

---

## 7. 复核后的下一步

1. 你确认第 2 节术语表 + 第 6 节 A1–A9。
2. 我按最终文案实施第一批：资源文件、`AppLanguage`、设置项、三个冲突的处理、单测、文档风险标注。
3. 交付时附上：`values/strings.xml` 与 `values-zh/strings.xml` 的键清单对照、`.\gradlew :common:testDebugUnitTest :mobile:lintDebug :mobile:assembleDebug` 结果。
4. 你上车目视（重点看：主页标语排版、设置页长文案换行、中文系统自动中文、切 English 后设置页立即变英文且**不会诱发自动连接**）。

---

## 8. 实施记录 · 第一批（已完成）

| 文件 | 改动 |
|---|---|
| `common/src/main/res/values/strings.xml` | 新增英文资源 148 条（含 `app_name`） |
| `common/src/main/res/values-zh/strings.xml` | 简体中文；键集合与英文逐字校验一致（148 = 148） |
| `AppLanguage.kt`（新增） | `resolve` / `effective` / `wrap`：只有显式选择才包装 `Configuration`，未选择时不干预系统 |
| `DiPlayBootstrap.kt` | `DiPlayPreferences` 新增 `language` 读写；`phoneName` 默认值改走资源 |
| `DiPlayActivity.kt` | `attachBaseContext`；全部界面文案资源化；设置页新增「语言 / Language」分组；`recreate` 守卫（`KEY_SKIP_AUTO_CONNECT`）；`CarPlaySize` 中文标签映射（报告仍用英文 `label`）；热点校验错误码映射；中文标语字距 `tracking()` |
| `DiPlaySessionService.kt` | `attachBaseContext` + 通知渠道/标题/正文/操作按钮资源化 |
| `CarPlayHostActivity.kt`、`ImageCropActivity.kt` | 第一批只加 `attachBaseContext`（机制先行）；第二批完成全部文案资源化 |
| `shared` 的 `CarPlaySize.kt` | `label` 注明为「仅供诊断报告与会话日志的稳定英文值」 |
| `shared` 的 `ManualHotspotValidation.kt` | 返回值由英文字面消息改为 `Problem` 错误码 |
| `shared` 的 `ManualHotspotValidationTest.kt` | 断言具体错误码（原来只断言非空） |
| `common/src/test/.../AppLanguageTest.kt`（新增） | 7 个用例：缺失/未知值不干预、英文车机保持英文、中文车机保持中文、zh-TW 也算中文、显式选英文覆盖中文车机、显式选中文覆盖英文车机、已存选择不被车机语言改动影响 |
| `common/src/test/.../LocalizedCopyTest.kt`（新增） | 3 个用例（纯 JVM，读源码 XML）：键集合完全一致、除白名单外中文不得与英文逐字相同、中文不得为空 |

**顺带修掉的一个隐性缺陷**：aapt 会裁掉字符串资源的首尾空格，原先用「`Reports save to Downloads/DiPlay. ` + 隐私说明」拼接会粘成一句。已改为整句资源 `report_note_downloads` / `report_note_choose`。

**顺带修掉的第二个缺陷**：`recreate()` 会让新实例重走 `onResume` 的自动连接逻辑，autoConnect 开启且无会话时切语言会偷偷发起连接。现由 `KEY_SKIP_AUTO_CONNECT` 标记挡住。

### 已知前提与残留风险

- **中文字体不做兜底**：依赖车机自带 CJK 字体；若某些车机缺字体，中文会显示为方块。这是本方案明确接受的取舍。
- 通知渠道名与**正在显示**的通知不跟随语言，下次会话生效。
- 投影中的界面不随语言切换重建，下次连接生效（会话内菜单本身已全部中文化）。

### 第二批（已完成）

| 范围 | 处理 |
|---|---|
| `CarPlayHostActivity` 连接状态面板（首屏） | 图标/标题/无线与 USB 提示/「返回 DiPlay」/三指下滑提示、复位按钮 |
| 设置菜单骨架 | 标题、`Connection` / `Location` / `Startup` / `Audio` / `Identity & appearance` / `Display & video` / `Window` / `Diagnostics` / `Android 9 compatibility` 九个分组标题、保存/退出/关闭按钮、Android 9 兼容性说明 |
| 各分区标签 | 无线 CarPlay、热点状态、自动启动、高级音频声道映射、分辨率、帧率、物理尺寸基准/长度、HEVC 与软件解码器、显示与视频、驾驶位、全屏（顶栏/底栏）、安全区域、AirPlay 图标、调试日志、位置上报、MFI 目标与服务器/令牌、厂商/型号/OEM 标签、Wi-Fi 会话（频段/信道/密码/安全类型） |
| 动态状态文案 | `friendlyStage()` **只翻右值**（左值匹配键原样不动）；`updateHotspotStatusBlock()` 改用 `hotspotStateLabel()` 映射；`safeAreaSummary()`、AirPlay 图标状态、分辨率预览面板、尺寸不可用 toast |
| 校验提示 | `validateMfiSettings()` / `validateManualHotspotSettings()` 的错误文案全部资源化（它们只显示在菜单里，不进日志） |
| `ImageCropActivity` | 8 条全部资源化（含新增 `host.R` 导入） |

**刻意保持英文（不翻）**：`hotspotModeLabel()`、`mfiTargetLabel()`、`CarPlayUiScale.label()`、`CarPlayDisplayScale.label()` 的输出（日志用）；`HotspotStatus.state`、`CarPlayStatus.describe()`、`friendlyStage` 左值、`appendLog`/`appendFileLog`/`setStatus` 入参、`finishSettingsMenu` 的 `"Settings saved"` 前缀、编解码器与协议标识（`H.264`、`HEVC (H.265)`、`LocalOnlyHotspot`、`USB/CH341`、`I2C`、`WPA2`、`WPA3`、`2.4 GHz`、`5 GHz`）、AirPlay 服务名 `"DiPlay"`、关闭按钮 `"X"`、数值刻度 `"0.3x"`/`"1.0x"`。

**又踩到一次 aapt 尾随空格坑**：分辨率预览面板原本是 `"Identity: " + 值` 这种前缀拼接，资源化时改为**带占位符的整行格式**（如 `Identity: %1$s / %2$s`），否则尾随空格会被裁掉导致文字粘连。

### 第二批验证结果（本地实跑）

命令同上：

```
.\gradlew.bat :shared:testDebugUnitTest :common:testDebugUnitTest :mobile:lintDebug :mobile:assembleDebug
```

| 检查 | 结果 |
|---|---|
| `:shared:testDebugUnitTest` | 通过 |
| `:common:testDebugUnitTest` | 通过：48 个测试 0 失败（`LocalizedCopyTest` 现在覆盖 **307** 个键 × 2 语言） |
| `:mobile:lintDebug` | 通过；`MissingTranslation` / `ExtraTranslation` **零告警**，告警总数与第一批完全一致（14 + 2 + 1，全部既有） |
| `:mobile:assembleDebug` | 通过，产出 `mobile-debug.apk`（40.5 MB） |
| APK 抽查 | `(zh)` 资源含第二批文案，如「安全区域：全屏 %1$s x %2$s」「调试日志」「退出应用」 |
| 资源规模 | `values/strings.xml` 与 `values-zh/strings.xml` 各 **307** 键，键集合逐字一致 |

### 第一批验证结果（本地实跑，2026-09）

命令（与 CI 一致）：

```
.\gradlew.bat :shared:testDebugUnitTest :common:testDebugUnitTest :mobile:lintDebug :mobile:assembleDebug
```

| 检查 | 结果 |
|---|---|
| `:shared:testDebugUnitTest` | 通过（含改写后的 `ManualHotspotValidationTest`） |
| `:common:testDebugUnitTest` | 通过：48 个测试，0 失败（`AppLanguageTest` 7、`LocalizedCopyTest` 3、原有 38） |
| `:mobile:lintDebug` | 通过；`MissingTranslation` / `ExtraTranslation` 零告警。报告里仅有既有告警：`SetTextI18n` 14、`TrustAllX509TrustManager` 2、`UnusedResources` 1（`config_advanced_audio_channel_mapping`，与本次无关） |
| `:mobile:assembleDebug` | 通过，产物 `mobile/build/outputs/apk/debug/mobile-debug.apk`（39.9 MB） |
| APK 内容抽查 | `aapt2 dump resources` 可见 `(zh)` 资源，中文文案确实打进包 |

**测试策略说明（重要，避免后人踩坑）**：本仓库的本地单测**没有开启 `testIncludeAndroidResources`**，`R.string.*` 在单测里是桩 id，`getString` 会抛 `Resources$NotFoundException`。因此 `AppLanguageTest` 只断言 Configuration 层（强制/不干预、生效语言），文案本身由 `LocalizedCopyTest` 在源码层校验（键集合完全一致 + 除白名单外中文不得与英文逐字相同 + 中文不得为空）。翻译完整性另由 lint 门禁保证。

---

## 9. 合并 main → develop（冲突解决记录）

`main` 领先 `develop` 9 个提交（BYD 仪表盘地图、本地热点重构、0.2.6 发版准备）。合并时**只有两个文件冲突**，因为 git 已把大部分 `getString` 本地化和 main 的新代码自动合并：

| 位置 | main 的改动 | 处理 |
|---|---|---|
| `DiPlayActivity.kt` 导入 / 字段 / `onSaveInstanceState` | 新增 `CarPlayClusterDisplay`、`pendingCarHotspotSetup`、保存 `pending_car_hotspot` | **两边都要**：保留 main 的新字段与状态保存，同时保留 `CarPlaySize` 导入、`recreatingForLanguage` / `skipAutoConnectOnResume` / `KEY_SKIP_AUTO_CONNECT` 守卫 |
| `DiPlayActivity.kt` 初始化失败处理 | 新增 `Log.e` 并改写提示语 | 取 main 的逻辑，文案进资源（`setup_error` 已更新为 main 的新措辞） |
| `DiPlayActivity.kt` 主页连接提示 | 按热点模式给三种提示 | 取 main 的三分支逻辑，三条文案新增资源键 |
| `DiPlayActivity.kt` `settings()` | 重构为图标分区：新增「连接设置」「诊断」，`section()` 增加 `icon` 参数 | 取 main 的结构与图标，标题/正文全部走资源；「语言」分区保留在「权限」与「关于」之间 |
| `DiPlayActivity.kt` BYD / 仪表盘分区 | 新增仪表盘地图、主题跟随、Usage Access、主题/对比度/地图尺寸/车辆标记等开关 | 取 main 的全部逻辑，文案新增资源键；枚举标签 `DiLink51ClusterLayout.Theme/Contrast.label` 保持英文供日志使用，UI 走 `clusterThemeLabel()` / `clusterContrastLabel()` 映射 |
| `DiPlayActivity.kt` `wirelessLinkControls()` | 重写为「车机自带热点 / Wi-Fi Direct」双选卡片 + 热点设置步骤 | 取 main 的整段实现，文案全量资源化 |
| `DiPlayActivity.kt` `askHotspotCredentials()` | 重写为带输入框、显示密码、实时错误、键盘控制的自定义对话框 | 取 main 的实现，标题/提示/按钮资源化 |
| `DiPlayActivity.kt` 报告保存对话框 | 新增「分享」按钮与 share Intent | 取 main 的实现，按钮与 chooser 标题资源化 |
| `CarPlayHostActivity.kt` 会话内热点模式列表 | 移除 `LocalOnlyHotspot` 选项，`Manual hotspot` 改名 `Built-in car hotspot` | 取 main 的列表，标签走新键 `carmenu_mode_builtin_hotspot` |

**顺带清理**：删除 15 个因 main 重写界面而失效的资源键（`home_pair_hint`、`section_wireless_connection`、`wireless_link_*`、`action_hotspot_*_named`、`dialog_hotspot_*_title`、`hotspot_manual_note`、`value_none`、`wireless_p2p_note`、`section_about_diagnostics`、`carmenu_mode_manual_hotspot`）；删除因同类重写而变成死代码的 `textInput()` 辅助函数。

**合并后资源规模**：`values/strings.xml` 与 `values-zh/strings.xml` 各 **379** 键，键集合逐字一致。`LocalizedCopyTest` 的「刻意同文」白名单新增 `cluster_marker_step`（纯格式串）与 `wireless_mode_wifi_direct`（Wi-Fi 联盟专名）。
