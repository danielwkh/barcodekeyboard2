# 微信输入法公开资料核对

研究日期：2026-10-18。本文仅记录公开页面；不代表复制其商标、素材或私有实现。

## 官方确认的功能

Google Play 产品页说明：微信输入法主打六种键盘模式、语音转文字、常用语、手写找字、拼写检查。近期更新写有跨设备功能可自定义设备名称，以及长按候选词置顶或删除。官方 Android 更新索引标示 3.5.5 于 2026-10-09 发布。

Apple App Store 产品说明另列出拼写 Plus、AI 问答、表情推荐／定制表情、剪贴板等；注意它不是 Android 所有版本皆相同的功能承诺。

少数派 2022 体验文章记载早期基本输入方式：全拼／双拼、手写、笔画、五笔；Android 另有剪贴板历史和悬浮键盘。文章也记录首字母大写、双击句号、智能空格、光标移动、常用语、触感设置等体验。此文章较旧，不能视为当前版本完整规格。

## 隐私和权限边界

官方隐私政策区分本机隐私保护模式与可选的拼写 Plus。基础输入、个人词典、常用语及剪贴板历史可以本机处理；语音转文字需麦克风权限，并在使用期间上传语音作实时处理；边写边译上传输入文字；跨设备粘贴／词库同步涉及上传设备关联数据及对应内容。若开发者不实施真实服务，不得用空按钮假称已支持云端功能。

## 来源

- 官方产品页：https://z.weixin.qq.com/
- 微信输入法 Android 更新索引：https://z.weixin.qq.com/web/change-log/android
- 官方 Android 3.5.5 记录：https://z.weixin.qq.com/web/change-log/175
- Google Play 官方产品页：https://play.google.com/store/apps/details?id=com.tencent.wetype&hl=zh
- Apple App Store 产品页：https://apps.apple.com/cn/app/%E5%BE%AE%E4%BF%A1%E8%BE%93%E5%85%A5%E6%B3%95/id1618175312
- 官方隐私政策：https://z.weixin.qq.com/web/privacy
- 少数派体验文章：https://sspai.com/post/77393
- Android 官方 IME 开发指南：https://developer.android.com/develop/ui/views/touch-and-input/creating-input-method

## 已能证实的差距

现有 Barcode Keyboard 2（v1.3.2）是原生 Android InputMethodService，拥有英文／拼音／笔画／速成简化键盘、内置 CameraX 条码／OCR 扫描、条码 OCR 数字回退、本机 XLSX 商品码表和扫描后自动 Enter。尚缺或不等价于微信输入法的项目包括：成熟九宫格／双拼／五笔／手写识别、成熟词频学习和长按候选管理、完整语音转写、拼写检查、设备剪贴板后台监听、同步、浮动键盘和完整设置面板。